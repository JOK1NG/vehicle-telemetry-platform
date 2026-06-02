# vehicle-telemetry-platform

车辆实时监控与轨迹回放平台，用于熟悉 Vue 3 + TypeScript + Spring Boot 后台业务开发。当前只做 MVP：工程初始化、登录与车辆台账、车辆模拟与实时监控。

## MVP 范围

- M0：前后端工程初始化，Docker Compose 启动 EMQX、TimescaleDB/PostGIS、Redis。
- M1：登录、JWT、RBAC、车辆台账 CRUD、前端车辆列表。
- M2：车辆模拟器、MQTT 数据接入、Redis 实时态、WebSocket/STOMP 推送、高德地图多车实时显示。

暂不纳入：历史轨迹回放、地理围栏、Kafka、轨迹抽稀、多车压测、CI/CD、复杂报表、复杂 ECharts 大屏。

## 目录结构

```text
.
├── backend/             # Spring Boot 后端
├── frontend/            # Vue 3 + TypeScript + Vite 前端
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

服务清单：

| 服务 | 端口 | 说明 |
|---|---:|---|
| EMQX MQTT | 1883 | 车辆遥测接入 |
| EMQX Dashboard | 18083 | 默认 `admin` / `public` |
| TimescaleDB/PostGIS | 5432 | PostgreSQL 16 + TimescaleDB + PostGIS |
| Redis | 6379 | 实时车辆状态缓存 |

常用命令：

```bash
docker compose logs -f emqx
docker compose logs -f timescaledb
docker compose logs -f redis
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

健康检查：

- `http://localhost:8080/health`
- `http://localhost:8080/actuator/health`

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev
```

默认地址：`http://localhost:5173`

开发期 `/api/*` 与 `/ws` 会通过 Vite 代理转发到 `http://localhost:8080`。

### 5. 验证实时链路（内置模拟器）

本地 `local` profile 默认启用车辆模拟器：

- 配置项：`SIMULATOR_ENABLED=true`
- 发布间隔：`SIMULATOR_INTERVAL_MS=1000`
- 车辆数量上限：`SIMULATOR_VEHICLE_LIMIT=20`

内置模拟器以 `vehicle` 表为车辆来源。先登录前端并在「车辆列表」创建至少一辆车，后端会按契约发布 MQTT 消息到 `vehicle/{vehicleId}/telemetry`，再经 Redis 与 `/topic/vehicles` 推送到「监控大屏」。如果只想手工发布 MQTT，可设置 `SIMULATOR_ENABLED=false` 后重启后端。

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

## 项目文档

- `docs/车辆实时监控与轨迹回放平台-项目设计文档.md`
- `docs/mvp-00-implementation-contract.md`
