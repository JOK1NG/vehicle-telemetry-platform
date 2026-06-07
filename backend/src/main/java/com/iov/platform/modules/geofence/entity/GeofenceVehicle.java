package com.iov.platform.modules.geofence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

@Data
@TableName("geofence_vehicle")
public class GeofenceVehicle implements Serializable {

    @TableId(type = IdType.NONE)
    private Long geofenceId;
    private Long vehicleId;
    private OffsetDateTime createdAt;

    public GeofenceVehicle() {}

    public GeofenceVehicle(Long geofenceId, Long vehicleId) {
        this.geofenceId = geofenceId;
        this.vehicleId = vehicleId;
        this.createdAt = OffsetDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GeofenceVehicle that)) return false;
        return Objects.equals(geofenceId, that.geofenceId) && Objects.equals(vehicleId, that.vehicleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(geofenceId, vehicleId);
    }
}
