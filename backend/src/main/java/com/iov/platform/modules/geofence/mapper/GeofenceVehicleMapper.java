package com.iov.platform.modules.geofence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.iov.platform.modules.geofence.entity.GeofenceVehicle;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GeofenceVehicleMapper extends BaseMapper<GeofenceVehicle> {

    @Select("SELECT vehicle_id FROM geofence_vehicle WHERE geofence_id = #{geofenceId}")
    List<Long> findVehicleIdsByGeofence(@Param("geofenceId") Long geofenceId);

    @Select("SELECT geofence_id FROM geofence_vehicle WHERE vehicle_id = #{vehicleId}")
    List<Long> findGeofenceIdsByVehicle(@Param("vehicleId") Long vehicleId);

    @Delete("DELETE FROM geofence_vehicle WHERE geofence_id = #{geofenceId}")
    int deleteByGeofence(@Param("geofenceId") Long geofenceId);
}
