# vehicle-telemetry-platform

车辆实时监控与轨迹回放平台，用于熟悉 React + TypeScript + Spring Boot 后台业务开发。当前只做 MVP：工程初始化、登录与车辆台账、车辆模拟与实时监控。

## MVP 范围

- M0：前后端工程初始化，Docker Compose 启动 EMQX、TimescaleDB/PostGIS、Redis。
- M1：登录、JWT、RBAC、车辆台账 CRUD、前端车辆列表。
- M2：车辆模拟器、MQTT 数据接入、Redis 实时态、WebSocket/STOMP 推送、高德地图多车实时显示。

暂不纳入：历史轨迹回放、地理围栏、Kafka、轨迹抽稀、多车压测、CI/CD、复杂报表、复杂 ECharts 大屏。

## 目录结构

```text
.
├── backend/             # Spring Boot 后端
├── frontend/            # React 18 + TypeScript + Vite + Tailwind v4 前端
├── simulator/           # 车辆遥测模拟器（独立 Spring Boot 进程，发布 MQTT telemetry）
├── docker/init-db/      # 本地数据库初始化脚本
├── docs/                # 设计文档与 MVP 实现契约
├── docker-compose.yml   # 本地中间件
├── .env.example         # 唯一入仓的环境变量模板
└── README.md            # 项目入口说明
```

实现前先读 `docs/mvp-00-implementation-contract.md`。API、MQTT topic、WebSocket topic、角色权限、坐标系、环境变量命名以该契约为准。

## 快速开始

### 前置条件

- Docker + Docker Compose v2
- JDK 17
- Maven 3.9+
- Node.js 22 LTS
- Git

### 1. 配置环境变量

```bash
cp .env.example .env
```

`.env` 只用于本地开发，不入仓。前端在 `frontend/` 目录运行 Vite 时，可复制同一份模板：

```bash
cd frontend
cp ../.env.example .env
```

高德地图变量遵循契约命名：

- `VITE_AMAP_KEY`
- `VITE_AMAP_SECURITY_JS_CODE`

### 2. 启动本地中间件

```bash
docker compose up -d
docker compose ps
```

验证点：
- `docker compose ps` 应显示 `timescaledb`、`emqx`、`redis` 三个服务均为 `healthy`
- 如果某服务持续 `unhealthy`，先执行 `docker compose logs -f <服务名>` 查看原因

服务清单：

| 服务 | 端口 | 说明 |
|---|---:|---|
| EMQX MQTT | 1883 | 车辆遥测接入 |
| EMQX Dashboard | 18083 | 默认 `admin` / `public` |
| TimescaleDB/PostGIS | 5432 | PostgreSQL 16 + TimescaleDB + PostGIS |
| Redis | 6380 -> 6379 | 实时车辆状态缓存。宿主机默认使用 6380，避免和本机 Redis 冲突 |

> **Redis 端口注意**：如果本机安装了 Homebrew Redis，裸 `redis-cli` 通常会连到本机 `127.0.0.1:6379`，不是本项目的 Docker Redis。检查实时车辆缓存时必须显式使用 `-p 6380`。

常用命令：

```bash
docker compose logs -f emqx
docker compose logs -f timescaledb
docker compose logs -f redis
redis-cli -p 6380 -a change_me_redis_123 SMEMBERS vehicle:online
redis-cli -p 6380 -a change_me_redis_123 HGETALL vehicle:rt:1
docker compose down
docker compose down -v
```

### 3. 启动后端

确认当前 shell 使用 JDK 17：

```bash
java -version
```

如果本机有多个 JDK，请先切到 JDK 17，再运行 Maven。

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

验证点：
- `curl http://localhost:8080/health` 应返回 `vehicle-telemetry-backend is running`
- `curl http://localhost:8080/actuator/health` 应返回 `{"status":"UP"}`
- 首次启动 Flyway 会自动执行 `db/migration/V1__init_schema.sql` ~ `V4__seed_demo_vehicles.sql`
- 后端 `local` profile 默认启用**内置车辆模拟器**，启动后约 5 秒开始向 MQTT 发布遥测

> **默认账号（Flyway 迁移脚本预置）**
>
> | 角色 | 用户名 | 密码 | 权限 |
> |---|---|---|---|
> | ADMIN | `admin` | `admin123` | 车辆 CRUD + 监控大屏 |
> | VIEWER | `viewer` | `viewer123` | 只读：车辆列表 + 监控大屏 |

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:5173`

验证点：
- 浏览器打开 `http://localhost:5173/login`，应能看到登录页
- 使用 `admin` / `admin123` 登录后应进入「车辆列表」
- 使用 `viewer` / `viewer123` 登录后也能查看车辆列表，但无新增/编辑/删除按钮
- 左侧菜单「监控大屏」配置高德 Key 后应能加载高德地图并显示车辆标记；未配置 Key 时会显示提示，但登录、车辆列表与实时 WebSocket 链路仍可验证

开发期 `/api/*` 与 `/ws` 会通过 Vite 代理转发到 `http://localhost:8080`。

> **高德地图配置**
>
> 前端 `.env.local`（不入仓）可配置：
> ```bash
> VITE_AMAP_KEY=你的高德JS_API_Key
> VITE_AMAP_SECURITY_JS_CODE=你的安全密钥
> ```
> 未配置时监控大屏会提示需要配置 `VITE_AMAP_KEY`，但车辆列表、登录、WebSocket 仍可正常验证。

### 5. 验证实时链路（内置模拟器）

本地 `local` profile 默认启用车辆模拟器：

- 配置项：`SIMULATOR_ENABLED=true`
- 发布间隔：`SIMULATOR_INTERVAL_MS=1000`
- 车辆数量上限：`SIMULATOR_VEHICLE_LIMIT=20`

内置模拟器以 `vehicle` 表为车辆来源。先登录前端并在「车辆列表」创建至少一辆车，后端会按契约发布 MQTT 消息到 `vehicle/{vehicleId}/telemetry`，再经 Redis 与 `/topic/vehicles` 推送到「监控大屏」。如果只想手工发布 MQTT，可设置 `SIMULATOR_ENABLED=false` 后重启后端。

验证点（命令行）：
- `redis-cli -p 6380 -a change_me_redis_123 SMEMBERS vehicle:online` 应返回在线车辆 ID 集合
- `redis-cli -p 6380 -a change_me_redis_123 HGETALL vehicle:rt:1` 应返回车辆 1 的实时遥测字段（lng/lat/speed/heading/battery/ts）
- `curl -H "Authorization: Bearer <admin_token>" http://localhost:8080/api/vehicles/snapshot` 应返回所有在线车辆的实时快照

验证点（前端）：
- 打开「监控大屏」后，浏览器 DevTools → Network → WS，应能看到 `ws?token=xxx` 连接并收到 `/topic/vehicles` 消息帧
- 地图上的车辆标记应每隔约 1 秒移动一次位置
- 右侧「在线车辆」表格的速度、电量应持续刷新

> **注意**：内置模拟器和独立模拟器不要同时启用，否则会向 MQTT 双倍写入数据。

### 6. 启动独立车辆遥测模拟器（M2 联调）

模拟器是独立 Spring Boot 进程，启动后会向 EMQX `vehicle/{vehicleId}/telemetry`
持续发布 telemetry payload。详细说明见 `simulator/README.md`。

```bash
cd simulator
mvn spring-boot:run
```

模拟器与后端解耦：自身不查数据库，车辆 ID 通过 `simulator.vehicle-ids` 或
`simulator.vehicle-count` 静态配置（默认 1..5）。`backend/.../V4__seed_demo_vehicles.sql`
会预先在 `vehicle` 表中插入 5 辆演示车，让模拟器开箱即用。

## 开发约定

- 根目录是唯一 Git 仓库根，不要在项目内再次 `multica repo checkout` 或创建嵌套 `.git`。
- README 以根目录本文档为准；模块内只在确有必要时保留补充说明，避免脚手架模板 README 入仓。
- `.gitignore` 以根目录为准，覆盖前端、后端、IDE、本地环境变量和 Multica 本地运行产物。
- `node_modules/`、`dist/`、`target/`、`.env`、`.env.*` 不入仓。
- 后续实现 issue 交付前必须检查 `git status --short --branch`，确认没有嵌套仓库、生成物或本地密钥被加入。

## Git 管理环节

当前团队分工里没有单独的 Git 管理智能体。MVP 阶段建议补一个轻量的 Git Steward 环节，可以由 QA/Release 兼任，也可以后续拆成独立智能体：

- 实现类 issue 进入 review 前，检查分支、工作区状态、提交范围和未跟踪文件。
- 合并前确认 README、`.gitignore`、`.env.example` 与实际启动方式一致。
- 发现嵌套 worktree、误提交依赖目录、生成物或密钥时，先阻断后续 issue 推进。
- PR 或交付说明中明确本次验证命令和未覆盖风险。

## 常见问题（FAQ）

**Q1：后端启动报错 `Connection refused` to localhost:5432/6380/1883**
> 先执行 `docker compose up -d`，确认中间件全部 healthy 后再启动后端。如果中间件刚启动就立即启动后端，可能出现「服务还没就绪就连接」的 race condition，等 10 秒重试即可。

**Q2：登录成功但所有 API 返回 401**
> 检查前端 `request.ts` 拦截器是否正常将 `Bearer <token>` 写入请求头。若使用 curl 测试，必须显式带 `-H "Authorization: Bearer <token>"`。

**Q3：监控大屏黑屏或提示 "高德地图加载失败"**
> 确认 `frontend/.env.local` 已配置有效的 `VITE_AMAP_KEY` 和 `VITE_AMAP_SECURITY_JS_CODE`。Key 必须对应「Web端(JS API)」应用类型，且域名白名单包含 `localhost`。

**Q4：Redis 命令连不上或返回空集合**
> 本项目 Redis 映射到宿主机 `6380`，不是默认的 `6379`。请使用 `redis-cli -p 6380 -a change_me_redis_123`。

**Q5：vehicle:online 集合为空，但模拟器已启用**
> 内置模拟器以 `vehicle` 表为来源。检查 `vehicle` 表是否有数据：`docker exec iov-timescaledb psql -U iov_admin -d iov_platform -c "SELECT COUNT(*) FROM vehicle;"`。Flyway 默认会插入 5 辆演示车，如果表为空，可能是迁移未执行或后端未连上数据库。

**Q6：viewer 为什么也能看到「监控大屏」？**
> MVP 权限设计中，VIEWER 的只读范围包含监控大屏和车辆列表，只是没有车辆 CRUD 权限。详见 `docs/mvp-00-implementation-contract.md` §6。

## MVP 边界说明

| 功能 | 状态 | 说明 |
|---|---|---|
| 登录 / JWT / RBAC | ✅ 已验证 | ADMIN + VIEWER 两角色 |
| 车辆台账 CRUD | ✅ 已验证 | 前端列表 + 后端完整 CRUD |
| MQTT 遥测接入 | ✅ 已验证 | 内置模拟器 → EMQX → 后端消费 |
| Redis 实时态 | ✅ 已验证 | `vehicle:online` + `vehicle:rt:{id}` |
| WebSocket/STOMP 推送 | ✅ 已验证 | `/topic/vehicles` 500ms 节流广播 |
| 高德地图多车实时显示 | ✅ 已验证 | 前端 Dashboard 订阅并渲染标记 |
| 历史轨迹回放 | ❌ 不在 MVP | 未实现 |
| 地理围栏 | ❌ 不在 MVP | PostGIS 已就绪，但业务未实现 |
| Kafka | ❌ 不在 MVP | 未引入 |
| 轨迹抽稀 / 压测 | ❌ 不在 MVP | 未实现 |
| Stage A CI | ✅ 已验证 | GitHub Actions 执行前端 `npm ci`、`npx tsc -b`、`npm run build` |
| 复杂报表 / ECharts 大屏 | ❌ 不在 MVP | 未实现 |

## 项目文档

- `docs/车辆实时监控与轨迹回放平台-项目设计文档.md`
- `docs/mvp-00-implementation-contract.md`
