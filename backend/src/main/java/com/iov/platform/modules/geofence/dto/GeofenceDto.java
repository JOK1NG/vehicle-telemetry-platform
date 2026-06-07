package com.iov.platform.modules.geofence.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeofenceDto {
    private Long id;
    private String name;
    /** 'CIRCLE' | 'POLYGON' */
    private String type;
    private Double centerLng;
    private Double centerLat;
    private Double radiusM;
    /** POLYGON 顶点 */
    private List<LngLat> polygon;
    private Boolean enabled;
    private List<Long> vehicleIds;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LngLat {
        private double lng;
        private double lat;
    }
}
