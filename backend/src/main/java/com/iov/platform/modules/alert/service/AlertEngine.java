package com.iov.platform.modules.alert.service;

import com.iov.platform.modules.alert.entity.AlertRule;
import com.iov.platform.modules.geofence.service.GeofenceEvaluator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 告警检测引擎
 * - 速度超限、低电量：依赖 RealtimeService 写入 Redis 时调用 evaluateTelemetry(...)
 * - 离线：周期性任务（每 30s）扫描 vehicle:online 集合，对比 last_seen 时间戳
 * - 围栏进出：依赖 GeofenceEvaluator 判定，调用 evaluateGeofence(...)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertEngine {

    private final AlertService alertService;
    private final GeofenceEvaluator geofenceEvaluator;
    private final StringRedisTemplate redis;

    /** 车辆围栏内状态：vehicleId -> geofenceId (Set) */
    private final ConcurrentHashMap<Long, Set<Long>> vehicleInGeofences = new ConcurrentHashMap<>();

    /** 车辆最近一次遥测的时间戳（用于离线检测） */
    private final ConcurrentHashMap<Long, Instant> lastSeen = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "alert-engine");
        t.setDaemon(true);
        return t;
    });

    @PostConstruct
    public void init() {
        try {
            alertService.reloadRuleCache();
        } catch (Exception e) {
            log.warn("告警规则缓存初始化失败: {}", e.getMessage());
        }
        // 离线检测：每 30s 扫描一次
        scheduler.scheduleAtFixedRate(this::scanOffline, 30, 30, TimeUnit.SECONDS);
        log.info("告警引擎已启动");
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * 每次写入遥测时调用：检查超速/低电量/围栏进出
     */
    public void evaluateTelemetry(Long vehicleId, double lng, double lat, double speed, double battery, int status) {
        lastSeen.put(vehicleId, Instant.now());
        if (status == 0) return;  // 离线车辆不检测

        // 超速
        AlertRule speedRule = alertService.getRule("OVERSPEED");
        if (speedRule != null && Boolean.TRUE.equals(speedRule.getEnabled()) && speed > speedRule.getThreshold()) {
            alertService.fireAlert(vehicleId, "OVERSPEED",
                    String.format("车辆超速：当前 %.1f km/h（阈值 %.0f）", speed, speedRule.getThreshold()),
                    lng, lat, speedRule.getId(), null);
        }

        // 低电量
        AlertRule batteryRule = alertService.getRule("LOW_BATTERY");
        if (batteryRule != null && Boolean.TRUE.equals(batteryRule.getEnabled()) && battery < batteryRule.getThreshold()) {
            alertService.fireAlert(vehicleId, "LOW_BATTERY",
                    String.format("电量偏低：当前 %.0f%%（阈值 %.0f%%）", battery, batteryRule.getThreshold()),
                    lng, lat, batteryRule.getId(), null);
        }

        // 围栏进出
        evaluateGeofence(vehicleId, lng, lat);
    }

    /**
     * 围栏进出判定
     */
    public void evaluateGeofence(Long vehicleId, double lng, double lat) {
        Set<Long> currentInside = geofenceEvaluator.findContainingGeofenceIds(vehicleId, lng, lat);

        Set<Long> wasInside = vehicleInGeofences.getOrDefault(vehicleId, java.util.Collections.emptySet());

        // 进入：之前不在，现在在
        for (Long gfId : currentInside) {
            if (!wasInside.contains(gfId)) {
                String name = geofenceEvaluator.getGeofenceName(gfId);
                alertService.fireAlert(vehicleId, "GEOFENCE_ENTER",
                        String.format("车辆进入围栏：%s", name != null ? name : ("#" + gfId)),
                        lng, lat, null, gfId);
            }
        }

        // 离开：之前在，现在不在
        for (Long gfId : wasInside) {
            if (!currentInside.contains(gfId)) {
                String name = geofenceEvaluator.getGeofenceName(gfId);
                alertService.fireAlert(vehicleId, "GEOFENCE_EXIT",
                        String.format("车辆离开围栏：%s", name != null ? name : ("#" + gfId)),
                        lng, lat, null, gfId);
            }
        }

        if (!currentInside.isEmpty() || !wasInside.isEmpty()) {
            vehicleInGeofences.put(vehicleId, currentInside);
        }
    }

    /**
     * 离线检测：扫 vehicle:online 集合，对照 lastSeen
     */
    private void scanOffline() {
        try {
            AlertRule rule = alertService.getRule("OFFLINE");
            if (rule == null || Boolean.FALSE.equals(rule.getEnabled())) return;
            double threshold = rule.getThreshold();
            if (Double.isNaN(threshold) || threshold <= 0) {
                log.warn("告警规则 OFFLINE 阈值异常: {}", threshold);
                return;
            }
            int thresholdSec = (int) Math.min(threshold * 60, Integer.MAX_VALUE);

            Set<String> onlineIds = redis.opsForSet().members("vehicle:online");
            if (onlineIds == null) return;

            Instant now = Instant.now();
            for (String idStr : onlineIds) {
                try {
                    Long vehicleId = Long.parseLong(idStr);
                    Instant last = lastSeen.get(vehicleId);
                    if (last == null) continue;  // 没记录过，不告警
                    long elapsed = Duration.between(last, now).getSeconds();
                    if (elapsed < thresholdSec) continue;

                    // 查 rt hash 是否还有数据（TTL 10s 内会被清掉）
                    Map<Object, Object> rtData = redis.opsForHash().entries("vehicle:rt:" + vehicleId);
                    if (!rtData.isEmpty()) {
                        // 校验 Redis 中的时间戳是否新鲜，避免陈旧数据误重置 lastSeen
                        String tsStr = (String) rtData.get("ts");
                        if (tsStr != null && !tsStr.isBlank()) {
                            try {
                                Instant rtTs = Instant.parse(tsStr);
                                long rtAge = Duration.between(rtTs, now).getSeconds();
                                if (rtAge < thresholdSec) {
                                    lastSeen.put(vehicleId, now);
                                    continue;
                                }
                            } catch (Exception e) {
                                // ts 无效，继续离线判定
                            }
                        }
                    }

                    // 离线
                    String plateNo = "";
                    try {
                        String meta = redis.opsForValue().get("vehicle:meta:" + vehicleId);
                        if (meta != null && !meta.isEmpty()) {
                            String[] parts = meta.split(",", 2);
                            if (parts.length > 0) plateNo = parts[0];
                        }
                    } catch (Exception ignore) {}
                    alertService.fireAlert(vehicleId, "OFFLINE",
                            String.format("车辆离线：%s 已 %d 分钟无数据", plateNo, elapsed / 60),
                            null, null, rule.getId(), null);
                } catch (NumberFormatException ignore) {}
            }
        } catch (Exception e) {
            log.warn("离线扫描异常: {}", e.getMessage());
        }
    }
}
