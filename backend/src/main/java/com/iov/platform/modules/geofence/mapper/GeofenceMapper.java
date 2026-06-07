package com.iov.platform.modules.geofence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iov.platform.modules.geofence.entity.Geofence;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface GeofenceMapper extends BaseMapper<Geofence> {

    /**
     * 加载所有启用的围栏（含 geom 字段）— PostGIS 包含判定用
     */
    @Select("SELECT id, name, type, center_lng, center_lat, radius_m, ST_AsGeoJSON(geom) AS geom_text, enabled FROM geofence WHERE enabled = true")
    List<java.util.Map<String, Object>> findAllEnabledWithGeom();

    /**
     * 单个围栏的 GeoJSON
     */
    @Select("SELECT ST_AsGeoJSON(geom) AS geom_text FROM geofence WHERE id = #{id}")
    String findGeomGeoJson(@Param("id") Long id);
}
