package com.iov.platform.modules.geofence.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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

    @PostConstruct
    public void init() {
        log.info("GeofenceCache 初始化完成");
    }

    public synchronized void load(List<Map<String, Object>> rows) {
        Map<Long, Entry> next = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String name = (String) row.get("name");
            String geomText = (String) row.get("geom_text");
            next.put(id, new Entry(id, name, geomText));
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
        public Entry(Long id, String name, String geomText) {
            this.id = id;
            this.name = name;
            this.geomText = geomText;
        }
    }
}
