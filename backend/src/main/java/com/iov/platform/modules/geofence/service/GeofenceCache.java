package com.iov.platform.modules.geofence.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 围栏几何缓存：id -> {name, geomGeoJson}
 * 给 GeofenceEvaluator 用，避免每次都查 PostGIS
 */
@Slf4j
@Component
public class GeofenceCache {

    private volatile Map<Long, Entry> entries = new HashMap<>();

    public void load(List<Map<String, Object>> rows) {
        Map<Long, Entry> next = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String name = (String) row.get("name");
            String geomText = (String) row.get("geom_text");
            Set<Long> vehicleIds = toVehicleIdSet(row.get("vehicle_ids"));
            next.put(id, new Entry(id, name, geomText, vehicleIds));
        }
        this.entries = next;
    }

    public Set<Long> allIds() {
        return entries.keySet();
    }

    public String getName(Long id) {
        Entry e = entries.get(id);
        return e != null ? e.name : null;
    }

    public String getGeomGeoJson(Long id) {
        Entry e = entries.get(id);
        return e != null ? e.geomText : null;
    }

    public boolean appliesToVehicle(Long geofenceId, Long vehicleId) {
        Entry e = entries.get(geofenceId);
        if (e == null) return false;
        if (vehicleId == null) return true;
        return e.vehicleIds.isEmpty() || e.vehicleIds.contains(vehicleId);
    }

    public Map<Long, String> allGeoJson() {
        return entries.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().geomText));
    }

    public Map<Long, String> allNames() {
        return entries.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().name));
    }

    public int size() {
        return entries.size();
    }

    public static class Entry {
        public final Long id;
        public final String name;
        public final String geomText;
        public final Set<Long> vehicleIds;
        public Entry(Long id, String name, String geomText, Set<Long> vehicleIds) {
            this.id = id;
            this.name = name;
            this.geomText = geomText;
            this.vehicleIds = vehicleIds != null ? Set.copyOf(vehicleIds) : Set.of();
        }
    }

    private Set<Long> toVehicleIdSet(Object raw) {
        if (raw == null) return Set.of();
        try {
            if (raw instanceof Array sqlArray) {
                raw = sqlArray.getArray();
            }
        } catch (Exception e) {
            log.warn("解析围栏车辆绑定失败: {}", e.getMessage());
            return Set.of();
        }

        Set<Long> ids = new HashSet<>();
        if (raw instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addVehicleId(ids, item);
            }
        } else if (raw.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(raw);
            for (int i = 0; i < len; i++) {
                addVehicleId(ids, java.lang.reflect.Array.get(raw, i));
            }
        } else if (raw instanceof String s) {
            String normalized = s.replace("{", "").replace("}", "");
            for (String item : normalized.split(",")) {
                addVehicleId(ids, item);
            }
        } else {
            addVehicleId(ids, raw);
        }
        return ids;
    }

    private void addVehicleId(Set<Long> ids, Object item) {
        if (item == null) return;
        if (item instanceof Number n) {
            ids.add(n.longValue());
            return;
        }
        String raw = item.toString().trim();
        if (raw.isEmpty()) return;
        try {
            ids.add(Long.parseLong(raw));
        } catch (NumberFormatException e) {
            log.warn("忽略非法围栏车辆 ID: {}", raw);
        }
    }
}
