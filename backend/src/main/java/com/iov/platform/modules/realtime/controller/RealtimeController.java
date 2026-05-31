package com.iov.platform.modules.realtime.controller;

import com.iov.platform.common.Result;
import com.iov.platform.modules.realtime.dto.VehicleSnapshot;
import com.iov.platform.modules.vehicle.mapper.VehicleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实时数据接口
 * GET /api/vehicles/snapshot - 车辆实时快照
 *
 * TODO(tech-debt): 快照接口对每个在线车辆逐条 selectById 查询 plateNo，
 * 100 辆车 = 100 次 DB 查询。MVP 阶段可接受，后续应改为批量查询或 Redis 缓存。
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class RealtimeController {

    private final StringRedisTemplate redis;
    private final VehicleMapper vehicleMapper;

    @GetMapping("/api/vehicles/snapshot")
    public Result<List<VehicleSnapshot>> snapshot() {
        Set<String> onlineIds = redis.opsForSet().members("vehicle:online");
        if (onlineIds == null || onlineIds.isEmpty()) {
            return Result.ok(List.of());
        }

        List<VehicleSnapshot> snapshots = new ArrayList<>();

        for (String idStr : onlineIds) {
            try {
                Long vehicleId = Long.parseLong(idStr);

                String rtKey = "vehicle:rt:" + vehicleId;
                Map<Object, Object> rtData = redis.opsForHash().entries(rtKey);
                if (rtData.isEmpty()) {
                    continue;  // TTL 已过期，由 RealtimeService 定时清理
                }

                // N+1 查询 — MVP 接受 (修复 #10)
                String plateNo = "";
                try {
                    var vehicle = vehicleMapper.selectById(vehicleId);
                    if (vehicle != null) {
                        plateNo = vehicle.getPlateNo() != null ? vehicle.getPlateNo() : "";
                    }
                } catch (Exception e) {
                    log.debug("查询车辆 plateNo 失败 vehicleId={}: {}", vehicleId, e.getMessage());
                }

                VehicleSnapshot snap = VehicleSnapshot.builder()
                        .vehicleId(vehicleId)
                        .plateNo(plateNo)
                        .lng(toDouble(rtData, "lng"))
                        .lat(toDouble(rtData, "lat"))
                        .speed(toDouble(rtData, "speed"))
                        .heading(toDouble(rtData, "heading"))
                        .battery(toDouble(rtData, "battery"))
                        .status(1)
                        .lastTs(toString(rtData, "ts"))
                        .build();

                snapshots.add(snap);
            } catch (NumberFormatException e) {
                log.warn("vehicle:online 中包含非法 ID: {}", idStr);
            }
        }

        return Result.ok(snapshots);
    }

    private double toDouble(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return 0;
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String toString(Map<Object, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }
}
