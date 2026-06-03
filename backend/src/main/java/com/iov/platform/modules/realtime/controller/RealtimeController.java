package com.iov.platform.modules.realtime.controller;

import com.iov.platform.common.Result;
import com.iov.platform.modules.realtime.dto.VehicleSnapshot;
import com.iov.platform.modules.vehicle.service.VehicleService;
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
 * 优化：采用 Redis 缓存（Cache-Aside / Multi-Get 批量缓存拉取），解决 DB N+1 查询，支持生产环境高并发。
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class RealtimeController {

    private final StringRedisTemplate redis;
    private final VehicleService vehicleService;

    @GetMapping("/api/vehicles/snapshot")
    public Result<List<VehicleSnapshot>> snapshot() {
        Set<String> onlineIds = redis.opsForSet().members("vehicle:online");
        if (onlineIds == null || onlineIds.isEmpty()) {
            return Result.ok(List.of());
        }

        // 收集所有合法的车辆 ID，准备进行 Redis 批量获取
        List<Long> vehicleIds = new ArrayList<>();
        for (String idStr : onlineIds) {
            try {
                vehicleIds.add(Long.parseLong(idStr));
            } catch (NumberFormatException e) {
                log.warn("vehicle:online 中包含非法 ID: {}", idStr);
            }
        }

        if (vehicleIds.isEmpty()) {
            return Result.ok(List.of());
        }

        // 从 Redis 缓存批量拉取车辆基础数据 (MGET)，未命中的车辆懒加载加载到缓存中
        Map<Long, String> vehicleMetaMap = vehicleService.getVehicleMetaCacheBatch(vehicleIds);

        List<VehicleSnapshot> snapshots = new ArrayList<>();

        for (String idStr : onlineIds) {
            try {
                Long vehicleId = Long.parseLong(idStr);

                String rtKey = "vehicle:rt:" + vehicleId;
                Map<Object, Object> rtData = redis.opsForHash().entries(rtKey);
                if (rtData.isEmpty()) {
                    continue;  // TTL 已过期，由 RealtimeService 定时清理
                }

                // 从缓存元数据中获取 plateNo 和 status
                String plateNo = "";
                int status = 0;  // 默认离线
                String cachedVal = vehicleMetaMap.get(vehicleId);
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
                }

                // 既然在 vehicle:online 集合中且有实时数据，覆盖为在线状态
                int onlineStatus = (status == 0) ? 1 : status;

                VehicleSnapshot snap = VehicleSnapshot.builder()
                        .vehicleId(vehicleId)
                        .plateNo(plateNo)
                        .lng(toDouble(rtData, "lng"))
                        .lat(toDouble(rtData, "lat"))
                        .speed(toDouble(rtData, "speed"))
                        .heading(toDouble(rtData, "heading"))
                        .battery(toDouble(rtData, "battery"))
                        .status(onlineStatus)
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
