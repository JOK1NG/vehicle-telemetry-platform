package com.iov.platform.modules.realtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.modules.realtime.dto.TelemetryMessage;
import com.iov.platform.modules.realtime.dto.VehicleUpdateMessage;
import com.iov.platform.modules.vehicle.entity.Vehicle;
import com.iov.platform.modules.vehicle.mapper.VehicleMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.TimeUnit;

/**
 * 实时遥测处理服务
 * MQTT 消息 -> Redis 实时态 -> DB 持久化 -> WebSocket 广播
 */
@Service
@Slf4j
public class RealtimeService {

    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbcTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final VehicleMapper vehicleMapper;

    /** 500ms 节流缓冲区。使用 ConcurrentHashMap 保证线程安全，swap 引用避免与 flush 竞态 */
    private volatile Map<Long, VehicleUpdateMessage> buffer = new ConcurrentHashMap<>();

    /** 缓存车辆存在性校验结果，避免每条消息都查库 */
    private final Set<Long> knownVehicleIds = ConcurrentHashMap.newKeySet();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-flush");
                t.setDaemon(true);
                return t;
            });

    public RealtimeService(StringRedisTemplate redis,
                           JdbcTemplate jdbcTemplate,
                           SimpMessagingTemplate messagingTemplate,
                           ObjectMapper objectMapper,
                           VehicleMapper vehicleMapper) {
        this.redis = redis;
        this.jdbcTemplate = jdbcTemplate;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
        this.vehicleMapper = vehicleMapper;

        // 每 500ms flush 缓冲区 (swap 模式，ConcurrentHashMap 保证线程安全)
        scheduler.scheduleAtFixedRate(this::flushBuffer, 500, 500, TimeUnit.MILLISECONDS);

        // 每 30s 清理 vehicle:online 集合中的过期车辆
        scheduler.scheduleAtFixedRate(this::cleanStaleOnlineSet, 30, 30, TimeUnit.SECONDS);

        // 每 60s 清理 knownVehicleIds 缓存，防止已删除车辆的 ID 一直残留
        scheduler.scheduleAtFixedRate(this::cleanKnownVehicleCache, 60, 60, TimeUnit.SECONDS);
    }

    /**
     * 处理一条 MQTT 遥测消息
     */
    @SuppressWarnings("null")
    public void handleTelemetry(Message<?> message) {
        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        String payload = (String) message.getPayload();

        if (topic == null || payload == null) {
            log.warn("MQTT 消息缺少 topic 或 payload");
            return;
        }

        Long vehicleId = extractVehicleId(topic);
        if (vehicleId == null) {
            log.warn("无法从 topic 解析 vehicleId: {}", topic);
            return;
        }

        // 校验车辆是否存在于 vehicle 表（缓存加速）
        if (!isKnownVehicle(vehicleId)) {
            log.warn("MQTT 遥测: vehicleId={} 不存在于 vehicle 表，忽略消息", vehicleId);
            return;
        }

        TelemetryMessage tm;
        try {
            tm = objectMapper.readValue(payload, TelemetryMessage.class);
        } catch (JsonProcessingException e) {
            log.warn("遥测 JSON 解析失败 vehicleId={}: {}", vehicleId, e.getMessage());
            return;
        }

        // null 字段校验：缺失必要字段时拒绝消息
        if (tm.getLng() == null || tm.getLat() == null) {
            log.warn("遥测缺少必填字段 vehicleId={} lng={} lat={}", vehicleId, tm.getLng(), tm.getLat());
            return;
        }

        // 字段范围校验
        if (!isValidTelemetry(tm, vehicleId)) {
            return;
        }

        // 时间戳解析 — 失败则拒绝消息
        OffsetDateTime ts = parseTs(tm.getTs());
        if (ts == null) {
            log.warn("遥测时间戳无效 vehicleId={} ts={}", vehicleId, tm.getTs());
            return;
        }

        // 1. 写 Redis 实时态
        String rtKey = "vehicle:rt:" + vehicleId;
        Map<String, String> rtData = new LinkedHashMap<>();
        rtData.put("lng", String.valueOf(tm.getLng()));
        rtData.put("lat", String.valueOf(tm.getLat()));
        rtData.put("speed", String.valueOf(tm.getSpeed() != null ? tm.getSpeed() : 0));
        rtData.put("heading", String.valueOf(tm.getHeading() != null ? tm.getHeading() : 0));
        rtData.put("battery", String.valueOf(tm.getBattery() != null ? tm.getBattery() : 0));
        rtData.put("ts", tm.getTs());
        redis.opsForHash().putAll(rtKey, rtData);
        redis.expire(rtKey, java.time.Duration.ofSeconds(10));

        // 2. 标记在线
        redis.opsForSet().add("vehicle:online", String.valueOf(vehicleId));

        // 3. 写 telemetry 表 (含 geom 字段)
        try {
            jdbcTemplate.update(
                    "INSERT INTO telemetry (time, vehicle_id, lng, lat, speed, heading, battery, fault_code, geom) "
                  + "VALUES (?::timestamptz, ?, ?, ?, ?, ?, ?, ?, "
                  + "ST_SetSRID(ST_MakePoint(?, ?), 4326))",
                    ts, vehicleId, tm.getLng(), tm.getLat(),
                    tm.getSpeed() != null ? tm.getSpeed() : 0,
                    tm.getHeading() != null ? tm.getHeading() : 0,
                    tm.getBattery() != null ? tm.getBattery() : 0,
                    tm.getFaultCode(),
                    tm.getLng(), tm.getLat()
            );
        } catch (Exception e) {
            log.error("写入 telemetry 表失败 vehicleId={}: {}", vehicleId, e.getMessage());
        }

        // 4. 获取 plateNo 和 status（采用 Redis 缓存防击穿）
        String plateNo = "";
        int status = 0;  // 默认离线
        String cachedVal = getVehicleMetaFromCache(vehicleId);
        if (cachedVal != null) {
            String[] parts = cachedVal.split(",", 2);
            if (parts.length >= 1) {
                plateNo = parts[0];
            }
            if (parts.length >= 2) {
                try {
                    status = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    status = 0;
                }
            }
        } else {
            status = 1; // 兜底：在线车辆默认状态为 1
        }

        // 5. 放入节流缓冲区
        buffer.put(vehicleId, new VehicleUpdateMessage(
                vehicleId, plateNo, tm.getLng(), tm.getLat(),
                tm.getSpeed() != null ? tm.getSpeed() : 0,
                tm.getHeading() != null ? tm.getHeading() : 0,
                tm.getBattery() != null ? tm.getBattery() : 0,
                status
        ));

        log.debug("收到遥测 vehicleId={} speed={} battery={}", vehicleId, tm.getSpeed(), tm.getBattery());
    }

    // ---- 车辆存在性校验（带缓存）----

    private boolean isKnownVehicle(Long vehicleId) {
        if (knownVehicleIds.contains(vehicleId)) {
            return true;
        }
        try {
            Vehicle vehicle = vehicleMapper.selectById(vehicleId);
            if (vehicle != null) {
                knownVehicleIds.add(vehicleId);
                return true;
            }
        } catch (Exception e) {
            log.warn("查询车辆存在性失败 vehicleId={}: {}", vehicleId, e.getMessage());
        }
        return false;
    }

    private void cleanKnownVehicleCache() {
        knownVehicleIds.clear();
        log.debug("已清理 knownVehicleIds 缓存");
    }

    // ---- 字段校验 ----

    private boolean isValidTelemetry(TelemetryMessage tm, Long vehicleId) {
        if (tm.getLng() < -180 || tm.getLng() > 180) {
            log.warn("遥测经度越界 vehicleId={} lng={}", vehicleId, tm.getLng());
            return false;
        }
        if (tm.getLat() < -90 || tm.getLat() > 90) {
            log.warn("遥测纬度越界 vehicleId={} lat={}", vehicleId, tm.getLat());
            return false;
        }
        if (tm.getSpeed() != null && tm.getSpeed() < 0) {
            log.warn("遥测速度负值 vehicleId={} speed={}", vehicleId, tm.getSpeed());
            return false;
        }
        if (tm.getBattery() != null && (tm.getBattery() < 0 || tm.getBattery() > 100)) {
            log.warn("遥测电量越界 vehicleId={} battery={}", vehicleId, tm.getBattery());
            return false;
        }
        if (tm.getHeading() != null && (tm.getHeading() < 0 || tm.getHeading() > 360)) {
            log.warn("遥测航向越界 vehicleId={} heading={}", vehicleId, tm.getHeading());
            return false;
        }
        return true;
    }

    // ---- 节流广播 ----

    void flushBuffer() {
        if (buffer.isEmpty()) return;

        // swap: 原子替换引用，避免 flush 期间丢失并发写入
        Map<Long, VehicleUpdateMessage> toFlush = buffer;
        buffer = new ConcurrentHashMap<>();

        List<VehicleUpdateMessage> batch = new ArrayList<>(toFlush.values());

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "VEHICLE_UPDATE");
        envelope.put("timestamp", Instant.now().toString());
        envelope.put("vehicles", batch);

        try {
            messagingTemplate.convertAndSend("/topic/vehicles", envelope);
        } catch (Exception e) {
            log.error("WebSocket 广播失败: {}", e.getMessage());
        }
    }

    // ---- 在线集合清理 ----

    public void cleanStaleOnlineSet() {
        try {
            Set<String> onlineIds = redis.opsForSet().members("vehicle:online");
            if (onlineIds == null || onlineIds.isEmpty()) return;

            for (String idStr : onlineIds) {
                String rtKey = "vehicle:rt:" + idStr;
                Boolean exists = redis.hasKey(rtKey);
                if (Boolean.FALSE.equals(exists)) {
                    redis.opsForSet().remove("vehicle:online", idStr);
                    log.debug("移除过期在线车辆: {}", idStr);
                }
            }
        } catch (Exception e) {
            log.error("清理 vehicle:online 失败: {}", e.getMessage());
        }
    }

    // ---- 优雅关闭 ----

    @PreDestroy
    public void shutdown() {
        log.info("RealtimeService 关闭中，flush 剩余缓冲区...");
        flushBuffer();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // ---- plateNo 和 status 查询（Redis 缓存优化，防击穿） ----

    private String getVehicleMetaFromCache(Long vehicleId) {
        // 优先从 buffer 获取，避免重复请求 Redis
        VehicleUpdateMessage existing = buffer.get(vehicleId);
        if (existing != null && existing.getPlateNo() != null && !existing.getPlateNo().isEmpty()) {
            return existing.getPlateNo() + "," + existing.getStatus();
        }

        String key = "vehicle:meta:" + vehicleId;
        try {
            String val = redis.opsForValue().get(key);
            if (val != null) {
                return val;
            }
        } catch (Exception e) {
            log.error("从 Redis 获取车辆基础缓存失败 vehicleId={}", vehicleId, e);
        }

        // 缓存未命中，从DB查询并写回缓存
        try {
            Vehicle vehicle = vehicleMapper.selectById(vehicleId);
            if (vehicle != null) {
                String newVal = (vehicle.getPlateNo() != null ? vehicle.getPlateNo() : "") + "," + (vehicle.getStatus() != null ? vehicle.getStatus() : 0);
                try {
                    redis.opsForValue().set(key, newVal, 24L, TimeUnit.HOURS);
                } catch (Exception ex) {
                    log.error("写入车辆基础缓存失败 vehicleId={}", vehicleId, ex);
                }
                return newVal;
            }
        } catch (Exception e) {
            log.warn("从DB查询并缓存车辆元数据失败 vehicleId={}: {}", vehicleId, e.getMessage());
        }
        return null;
    }

    // ---- 工具方法 ----

    private Long extractVehicleId(String topic) {
        if (topic == null || topic.isEmpty()) return null;
        String[] parts = topic.split("/");
        if (parts.length >= 3 && "vehicle".equals(parts[0])) {
            try {
                return Long.parseLong(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private OffsetDateTime parseTs(String ts) {
        if (ts == null || ts.isBlank()) return null;
        try {
            return OffsetDateTime.parse(ts, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception e) {
            return null;
        }
    }
}
