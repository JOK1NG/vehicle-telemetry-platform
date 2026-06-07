package com.iov.platform.modules.geofence.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.common.ResourceNotFoundException;
import com.iov.platform.modules.geofence.dto.GeofenceDto;
import com.iov.platform.modules.geofence.entity.Geofence;
import com.iov.platform.modules.geofence.entity.GeofenceVehicle;
import com.iov.platform.modules.geofence.mapper.GeofenceMapper;
import com.iov.platform.modules.geofence.mapper.GeofenceVehicleMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeofenceService extends ServiceImpl<GeofenceMapper, Geofence> {

    private final GeofenceMapper geofenceMapper;
    private final GeofenceVehicleMapper geofenceVehicleMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final GeofenceCache cache;

    @PostConstruct
    public void init() {
        refreshCache();
    }

    /** 重新加载所有启用围栏的 geom（GeoJSON）到缓存 */
    public void refreshCache() {
        try {
            List<Map<String, Object>> rows = geofenceMapper.findAllEnabledWithGeom();
            List<Map<String, Object>> enriched = rows.stream().map(row -> {
                Map<String, Object> copy = new HashMap<>(row);
                Long id = ((Number) row.get("id")).longValue();
                copy.put("vehicle_ids", geofenceVehicleMapper.findVehicleIdsByGeofence(id));
                return copy;
            }).toList();
            cache.load(enriched);
            log.info("围栏缓存已刷新，共 {} 条启用围栏", enriched.size());
        } catch (Exception e) {
            log.error("刷新围栏缓存失败: {}", e.getMessage());
        }
    }

    // ---- CRUD ----

    public List<GeofenceDto> listAll() {
        List<Geofence> list = list(new LambdaQueryWrapper<Geofence>().orderByDesc(Geofence::getCreatedAt));
        return list.stream().map(this::toDto).toList();
    }

    public GeofenceDto get(Long id) {
        Geofence g = getById(id);
        if (g == null) throw new ResourceNotFoundException("围栏不存在");
        return toDto(g);
    }

    @Transactional
    public GeofenceDto create(GeofenceDto dto) {
        validate(dto);
        Geofence g = new Geofence();
        g.setName(dto.getName());
        g.setType(dto.getType());
        g.setEnabled(dto.getEnabled() == null ? true : dto.getEnabled());
        if ("CIRCLE".equals(dto.getType())) {
            g.setCenterLng(dto.getCenterLng());
            g.setCenterLat(dto.getCenterLat());
            g.setRadiusM(dto.getRadiusM());
        } else {
            g.setPolygon(serializePolygon(dto.getPolygon()));
        }
        g.setCreatedAt(OffsetDateTime.now());
        g.setUpdatedAt(OffsetDateTime.now());
        save(g);
        // PostGIS geom
        upsertGeom(g);
        // 关联车辆
        if (dto.getVehicleIds() != null) {
            bindVehicles(g.getId(), dto.getVehicleIds());
        }
        refreshCache();
        return toDto(getById(g.getId()));
    }

    @Transactional
    public GeofenceDto update(Long id, GeofenceDto dto) {
        Geofence g = getById(id);
        if (g == null) throw new ResourceNotFoundException("围栏不存在");
        if (dto.getName() != null) g.setName(dto.getName());
        if (dto.getEnabled() != null) g.setEnabled(dto.getEnabled());
        if ("CIRCLE".equals(g.getType())) {
            if (dto.getCenterLng() != null) g.setCenterLng(dto.getCenterLng());
            if (dto.getCenterLat() != null) g.setCenterLat(dto.getCenterLat());
            if (dto.getRadiusM() != null) g.setRadiusM(dto.getRadiusM());
        } else {
            if (dto.getPolygon() != null) g.setPolygon(serializePolygon(dto.getPolygon()));
        }
        g.setUpdatedAt(OffsetDateTime.now());
        updateById(g);
        upsertGeom(g);
        if (dto.getVehicleIds() != null) {
            geofenceVehicleMapper.deleteByGeofence(id);
            bindVehicles(id, dto.getVehicleIds());
        }
        refreshCache();
        return toDto(getById(id));
    }

    @Transactional
    public void delete(Long id) {
        Geofence g = getById(id);
        if (g == null) throw new ResourceNotFoundException("围栏不存在");
        geofenceVehicleMapper.deleteByGeofence(id);
        removeById(id);
        refreshCache();
    }

    public void bindVehicles(Long geofenceId, List<Long> vehicleIds) {
        if (vehicleIds == null) return;
        for (Long vid : vehicleIds) {
            if (vid == null) continue;
            GeofenceVehicle gv = new GeofenceVehicle(geofenceId, vid);
            try {
                geofenceVehicleMapper.insert(gv);
            } catch (DuplicateKeyException e) {
                log.debug("围栏-车辆关联已存在，跳过: geofenceId={}, vehicleId={}", geofenceId, vid);
            } catch (Exception e) {
                log.warn("绑定围栏车辆失败 geofenceId={}, vehicleId={}: {}", geofenceId, vid, e.getMessage());
            }
        }
    }

    public List<Long> getVehicleIds(Long geofenceId) {
        return geofenceVehicleMapper.findVehicleIdsByGeofence(geofenceId);
    }

    public List<Long> getGeofenceIdsForVehicle(Long vehicleId) {
        return geofenceVehicleMapper.findGeofenceIdsByVehicle(vehicleId);
    }

    // ---- 内部 ----

    private void validate(GeofenceDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("围栏名称不能为空");
        }
        if (dto.getType() == null || (!"CIRCLE".equals(dto.getType()) && !"POLYGON".equals(dto.getType()))) {
            throw new IllegalArgumentException("围栏类型必须是 CIRCLE 或 POLYGON");
        }
        if ("CIRCLE".equals(dto.getType())) {
            if (dto.getCenterLng() == null || dto.getCenterLat() == null || dto.getRadiusM() == null) {
                throw new IllegalArgumentException("圆形围栏必须提供中心点和半径");
            }
            if (dto.getRadiusM() <= 0) {
                throw new IllegalArgumentException("半径必须为正数");
            }
        } else {
            if (dto.getPolygon() == null || dto.getPolygon().size() < 3) {
                throw new IllegalArgumentException("多边形围栏至少需要 3 个顶点");
            }
        }
    }

    private void upsertGeom(Geofence g) {
        if ("CIRCLE".equals(g.getType())) {
            try {
                jdbcTemplate.update(
                        "UPDATE geofence SET geom = ST_Buffer(ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?)::geometry " +
                                "WHERE id = ?",
                        g.getCenterLng(), g.getCenterLat(), g.getRadiusM(), g.getId());
            } catch (Exception e) {
                log.error("更新 CIRCLE geom 失败 geofenceId={}: {}", g.getId(), e.getMessage());
            }
        } else {
            // 多边形解析失败必须抛异常，避免 polygon 有数据但 geom 为 null 的不一致状态
            List<GeofenceDto.LngLat> pts;
            if (g.getPolygon() == null || g.getPolygon().isBlank()) {
                throw new IllegalArgumentException("多边形围栏缺少 polygon 数据 geofenceId=" + g.getId());
            }
            try {
                pts = objectMapper.readValue(g.getPolygon(), new TypeReference<List<GeofenceDto.LngLat>>() {});
            } catch (Exception e) {
                throw new IllegalStateException("解析 polygon 失败 geofenceId=" + g.getId(), e);
            }
            if (pts == null || pts.isEmpty()) {
                throw new IllegalStateException("多边形围栏顶点为空 geofenceId=" + g.getId());
            }
            StringBuilder wkt = new StringBuilder("POLYGON((");
            for (int i = 0; i < pts.size(); i++) {
                if (i > 0) wkt.append(", ");
                wkt.append(pts.get(i).getLng()).append(" ").append(pts.get(i).getLat());
            }
            // 闭合：首尾相同
            wkt.append(", ").append(pts.get(0).getLng()).append(" ").append(pts.get(0).getLat());
            wkt.append("))");
            try {
                jdbcTemplate.update("UPDATE geofence SET geom = ST_GeomFromText(?, 4326) WHERE id = ?", wkt.toString(), g.getId());
            } catch (Exception e) {
                log.error("更新 POLYGON geom 失败 geofenceId={}: {}", g.getId(), e.getMessage());
            }
        }
    }

    private String serializePolygon(List<GeofenceDto.LngLat> polygon) {
        if (polygon == null) return null;
        try {
            return objectMapper.writeValueAsString(polygon);
        } catch (Exception e) {
            log.warn("polygon 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private GeofenceDto toDto(Geofence g) {
        GeofenceDto dto = new GeofenceDto();
        dto.setId(g.getId());
        dto.setName(g.getName());
        dto.setType(g.getType());
        dto.setCenterLng(g.getCenterLng());
        dto.setCenterLat(g.getCenterLat());
        dto.setRadiusM(g.getRadiusM());
        dto.setEnabled(g.getEnabled());
        dto.setCreatedAt(g.getCreatedAt());
        dto.setUpdatedAt(g.getUpdatedAt());
        // polygon
        if (g.getPolygon() != null && !g.getPolygon().isBlank()) {
            try {
                dto.setPolygon(objectMapper.readValue(g.getPolygon(), new TypeReference<List<GeofenceDto.LngLat>>() {}));
            } catch (Exception ignore) {}
        }
        // 关联车辆
        dto.setVehicleIds(getVehicleIds(g.getId()));
        return dto;
    }
}
