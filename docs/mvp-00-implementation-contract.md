# MVP-00 实现约束与接口契约

> 本文档收敛 MVP（M0+M1+M2）实现前必须确认的技术约束、版本锁定和跨端数据契约。
> 后续 Forge / Pi / OpenGrok 实现时以本文档为第一参考来源。

---

## 1. 版本矩阵 & 基础设施

### 1.1 后端

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | **17 (LTS)** | Spring Boot 3.x 最低要求，生态最成熟 |
| Spring Boot | **3.4.13** | 3.4.x 最新补丁版本，稳定 + 社区资料丰富 |
| Maven | **3.9+**（wrapper 锁定） | 设计文档指定 Maven 多模块 |
| Flyway | **随 spring-boot-starter 引入** | DB 迁移工具，比 Liquibase 轻量，MVP 够用 |
| MyBatis-Plus | **3.5.x** | 与 Spring Boot 3.4 兼容的最新稳定版 |

### 1.2 前端

| 组件 | 版本 | 说明 |
|---|---|---|
| Node.js | **22 LTS** | 当前活跃 LTS 版本 |
| 包管理器 | **npm**（随 Node 自带） | 学习项目简单起见，后续可切 pnpm |
| Vue | **3.5+** | Composition API + `<script setup>` 稳定版 |
| Vite | **6.x** | 当前最新稳定 |
| TypeScript | **5.x** | Vite 6.x 配套 |
| Element Plus | **2.9+** | Vue 3 组件库 |
| 高德地图 | **@amap/amap-jsapi-loader 1.x** | JS API 2.0 动态加载器 |

### 1.3 Docker 镜像

| 服务 | 镜像 | 说明 |
|---|---|---|
| EMQX | `emqx/emqx:5.8.8` | 5.x LTS 稳定版，端口 1883/18083 |
| TimescaleDB | `timescale/timescaledb-ha:pg16` | **含 PostGIS**，一个镜像全搞定 |
| Redis | `redis:7-alpine` | 轻量镜像 |

> ⚠️ **重要**：必须用 `timescaledb-ha` 而非 `timescaledb`。后者不含 PostGIS，地理围栏功能会缺依赖。

### 1.4 数据库迁移

- 使用 **Flyway**，SQL 脚本放 `src/main/resources/db/migration/`
- 命名约定：`V1__create_vehicle.sql`、`V2__create_telemery_hypertable.sql` ...
- TimescaleDB 的 `create_hypertable` 和 PostGIS 的 `CREATE EXTENSION postgis;` 在第一个迁移脚本中执行
- 初始用户数据（ADMIN/VIEWER）也在迁移脚本中插入

---

## 2. MQTT Topic 与 Telemetry Payload 契约

### 2.1 Topic 结构

```
上行（车→云）：vehicle/{vehicleId}/telemetry
下行（云→车，进阶）：vehicle/{vehicleId}/command   ← MVP 不实现
```

- `{vehicleId}` = 数据库 `vehicle.id`（**BIGSERIAL 自增整型**，MVP 阶段直接用数字 ID）
- 通配订阅：`vehicle/+/telemetry`
- QoS：**1**（至少送达一次）

### 2.2 Telemetry Payload

```json
{
  "ts": "2026-06-01T08:30:00.000Z",
  "lng": 121.473701,
  "lat": 31.230416,
  "speed": 42.5,
  "heading": 90.0,
  "battery": 78.3,
  "faultCode": null
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `ts` | string (ISO 8601 UTC) | ✅ | 遥测时间戳，毫秒精度 |
| `lng` | double | ✅ | 经度，**GCJ-02 坐标系** |
| `lat` | double | ✅ | 纬度，**GCJ-02 坐标系** |
| `speed` | double | ✅ | 速度 km/h |
| `heading` | double | ✅ | 航向角 0–360° |
| `battery` | double | ✅ | 电量 0–100% |
| `faultCode` | string? | ❌ | 故障码，无故障传 null 或不传 |

---

## 3. WebSocket / STOMP 主题与消息结构

### 3.1 STOMP 主题

| 目的地 | 方向 | 说明 |
|---|---|---|
| `/topic/vehicles` | Server → Client | 全量车辆实时位置批次，**500ms 节流广播** |
| `/topic/alerts` | Server → Client | 新告警事件 |
| `/user/queue/errors` | Server → Client | WebSocket 错误通知（MVP 可选） |

### 3.2 车辆实时位置消息（`/topic/vehicles`）

每 500ms 后端聚合一次所有在线车辆最新位置，广播给订阅者：

```json
{
  "type": "VEHICLE_UPDATE",
  "timestamp": "2026-06-01T08:30:00.500Z",
  "vehicles": [
    {
      "vehicleId": 1,
      "lng": 121.473701,
      "lat": 31.230416,
      "speed": 42.5,
      "heading": 90.0,
      "battery": 78.3,
      "status": 1
    }
  ]
}
```

- `status`：`0` = 离线，`1` = 在线（映射数据库 `vehicle.status`）

### 3.3 告警消息（`/topic/alerts`）

```json
{
  "type": "ALERT",
  "alert": {
    "id": 101,
    "vehicleId": 1,
    "type": "OVERSPEED",
    "level": 2,
    "message": "车辆超速：当前速度 120 km/h",
    "lng": 121.473701,
    "lat": 31.230416,
    "occurredAt": "2026-06-01T08:30:00.000Z"
  }
}
```

---

## 4. REST API 补充

设计文档 §5.2 已定义的接口不再重复，此处仅补充 MVP 新增接口。

### 4.1 车辆实时快照（MVP 必需）

前端首次连接或刷新时，需要一次性拿到所有在线车辆当前状态。

```
GET /api/vehicles/snapshot
```

**响应：**

```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "vehicleId": 1,
      "plateNo": "沪A12345",
      "lng": 121.473701,
      "lat": 31.230416,
      "speed": 42.5,
      "heading": 90.0,
      "battery": 78.3,
      "status": 1,
      "lastTs": "2026-06-01T08:30:00.000Z"
    }
  ]
}
```

**前端流程**：先调 `/api/vehicles/snapshot` 拿初始数据 → 再连 WebSocket 订阅增量更新。

**数据源**：Redis `vehicle:rt:{id}` 哈希全部字段 + `vehicle:online` 集合。

---

## 5. 高德地图 & 坐标系

### 5.1 环境变量

前端 `.env` / `.env.local`（**不入仓库**）：

```bash
VITE_AMAP_KEY=你的高德JS_API_Key
VITE_AMAP_SECURITY_JS_CODE=你的安全密钥
```

### 5.2 坐标系策略

**全链路 GCJ-02（火星坐标系）**：

- 模拟器**直接生成 GCJ-02 坐标**，不经转换即入库/入 Redis
- 数据库 `telemetry.geom` 字段 SRID=4326，但存的是 GCJ-02 经纬度
- 前端从 API/WebSocket 拿到坐标后直接上图，零转换

> ⚠️ 如果模拟器路线用 WGS-84（如 GPS 导出的轨迹），需要在模拟器内做一次 WGS-84 → GCJ-02 转换。推荐库：Java 版 `coordtransform`。

---

## 6. MVP RBAC 角色

| 角色 | 权限 |
|---|---|
| **ADMIN** | 登录 + 车辆 CRUD + 告警处理 + 监控大屏 |
| **VIEWER** | 登录 + 只读（监控大屏 + 车辆列表 + 轨迹回放） |

- JWT payload 中携带 `role` 字段
- Spring Security 按 role 做接口级鉴权
- MVP 只做两个角色，**不做角色管理 UI**
- 初始用户在 Flyway 迁移脚本中创建

### 6.1 JWT Payload 结构（草案）

```json
{
  "sub": "1",
  "username": "admin",
  "role": "ADMIN",
  "iat": 1748736000,
  "exp": 1748743200
}
```

---

## 7. Redis 键设计（确认）

设计文档 §4.6 的 Redis 键设计直接沿用，此处确认：

| Key | 类型 | 内容 | TTL |
|---|---|---|---|
| `vehicle:rt:{id}` | Hash | lng, lat, speed, heading, battery, ts | 10s |
| `vehicle:online` | Set | 在线 vehicleId 集合 | — |
| `alert:latest` | List | 最近 N 条告警 JSON | — |

> `vehicle:rt:{id}` 的 10s TTL 既做在线判定也做数据过期。后端每收到一条 MQTT 消息就刷新 TTL。

---

## 8. 已确认事项 & 待办

### ✅ 已确认

1. JDK 17 + Spring Boot 3.4.13 + Maven 3.9+
2. TimescaleDB-HA（含 PostGIS）而非纯 TimescaleDB
3. Flyway 做 DB 迁移
4. MQTT topic 用自增 vehicleId（MVP 简化）
5. 全链路 GCJ-02 坐标系，模拟器源头生成
6. RBAC 两个角色：ADMIN / VIEWER
7. WebSocket `/topic/vehicles` 用 500ms 节流广播
8. 需要 `/api/vehicles/snapshot` 快照接口

### ❌ 不在 MVP 范围

- 历史轨迹回放、地理围栏、Kafka、轨迹抽稀、多车压测、CI/CD、复杂报表、复杂 ECharts 大屏
- 车辆 ID 用 VIN/UUID 映射（MVP 用自增 ID 即可）
- 角色管理 UI
- 详细的速率限制和 API 网关
