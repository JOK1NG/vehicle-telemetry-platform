# vehicle-telemetry-simulator

MVP 级车辆遥测模拟器，独立 Spring Boot 进程。

## 职责

- 按配置生成 N 辆车的实时 telemetry payload（GCJ-02 坐标）
- 发布到 EMQX MQTT `vehicle/{vehicleId}/telemetry`，QoS 1
- 后端订阅 `vehicle/+/telemetry` 消费，写入 Redis 实时态 + TimescaleDB + WebSocket 广播

严格遵循 `docs/mvp-00-implementation-contract.md` §2.2 的 payload schema 和 topic 模板。

## 启动

```bash
# 1. 先确保 docker compose 中间件已起来（emqx / timescaledb / redis）
cd ../
docker compose up -d

# 2. 启动后端（含 Flyway 迁移；会跑 V4__seed_demo_vehicles.sql 灌入 5 辆演示车）
cd ../backend
mvn spring-boot:run -Dspring-boot.run.profiles=local

# 3. 启动模拟器
cd ../simulator
mvn spring-boot:run
```

也可打成可执行 jar 后台跑：

```bash
mvn -DskipTests package
java -jar target/vehicle-telemetry-simulator-0.0.1-SNAPSHOT.jar
```

## 配置

所有配置项前缀 `simulator.*`，可通过 `application.yml` 或环境变量 `SIMULATOR_*` 覆盖：

| Key | 默认值 | 说明 |
|---|---|---|
| `simulator.mqtt.url` | `tcp://localhost:1883` | EMQX MQTT 接入点 |
| `simulator.mqtt.username` / `password` | 空 | 留空即匿名连接 |
| `simulator.publish-interval-ms` | 1000 | 发布周期，1Hz=1s |
| `simulator.vehicle-ids` | 空 | 显式车辆 ID 列表，逗号分隔；设置后覆盖 `vehicle-count` |
| `simulator.vehicle-count` | 5 | 自动生成 `1..count` 范围内的车辆 ID |
| `simulator.base-center-lng` / `lat` | 121.473701 / 31.230416 | 初始位置中心（上海人民广场），GCJ-02 |
| `simulator.spread-lng` / `lat` | 0.05 / 0.045 | 经纬度扩散范围（度） |
| `simulator.min-speed` / `max-speed` | 20 / 60 | 速度 km/h 范围 |
| `simulator.fault-probability` | 0.0 | 故障码注入概率（0..1） |
| `simulator.auto-start` | true | 启动后是否自动开始发布 |

## 验证

模拟器启动后，EMQX Dashboard（http://localhost:18083，默认 admin/public）应能看到
`vehicle/+/telemetry` 主题下有稳定消息流入；同时 `vehicle:rt:*` 和 `vehicle:online`
两个 Redis key 也应被后端持续刷新（后端启动后才能观察到）。

如需快速验证 MQTT 链路，可以用一个临时订阅者：

```bash
# 在 docker 网络内
docker run --rm -it --network vehicle-telemetry-platform_iov-net eclipse-mosquitto \
  mosquitto_sub -h iov-emqx -p 1883 -t 'vehicle/+/telemetry' -v
```

或者在 EMQX Dashboard → 问题分析 → 消息跟踪中订阅 `vehicle/+/telemetry`。

## 注意

- 模拟器只产生 telemetry 上行消息；不订阅任何 topic，不接收命令（contract §2.1 中
  `vehicle/{vehicleId}/command` 是下行命令，MVP 不实现）。
- 模拟器与后端解耦：不查数据库；车辆 ID 通过配置静态注入，新增/删除车辆需要同步修改
  `simulator.vehicle-ids` 或 `simulator.vehicle-count`。
- 不模拟断网/丢包；联调告警引擎时可调高 `fault-probability` 注入故障码。
