package com.iov.platform.modules.geofence.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 围栏包含判定
 * 解析 GeoJSON Polygon 的 coordinates [[lng, lat], ...]，用平面多边形点-在-多边形内算法判断
 * 精度足够车辆围栏场景（城市级围栏，平面算法误差 < 1m）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GeofenceEvaluator {

    private static final double RAY_CASTING_EPSILON = 1e-12;

    private final GeofenceCache cache;
    private final ObjectMapper objectMapper;

    /**
     * 返回包含 (lng, lat) 的所有启用围栏 ID
     */
    public Set<Long> findContainingGeofenceIds(double lng, double lat) {
        Set<Long> result = new HashSet<>();
        for (Long id : cache.allIds()) {
            String geoJson = cache.getGeomGeoJson(id);
            if (geoJson == null) continue;
            try {
                if (contains(geoJson, lng, lat)) {
                    result.add(id);
                }
            } catch (Exception e) {
                log.warn("围栏 {} 包含判定失败: {}", id, e.getMessage());
            }
        }
        return result;
    }

    public String getGeofenceName(Long id) {
        return cache.getName(id);
    }

    private boolean contains(String geoJson, double lng, double lat) throws Exception {
        JsonNode root = objectMapper.readTree(geoJson);
        String type = root.path("type").asText();
        if ("Polygon".equals(type)) {
            JsonNode coords = root.path("coordinates");
            if (coords.isArray() && coords.size() > 0) {
                // 第一个环是外环
                return pointInRing(lng, lat, coords.get(0));
            }
        }
        return false;
    }

    /**
     * Ray casting 算法判断点是否在多边形环内
     */
    private boolean pointInRing(double lng, double lat, JsonNode ring) {
        if (!ring.isArray() || ring.size() < 3) return false;
        boolean inside = false;
        int n = ring.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = ring.get(i).get(0).asDouble();
            double yi = ring.get(i).get(1).asDouble();
            double xj = ring.get(j).get(0).asDouble();
            double yj = ring.get(j).get(1).asDouble();
            boolean intersect = ((yi > lat) != (yj > lat)) &&
                    (lng < (xj - xi) * (lat - yi) / (yj - yi + RAY_CASTING_EPSILON) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }
}
