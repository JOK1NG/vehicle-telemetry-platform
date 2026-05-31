package com.iov.platform.modules.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 车辆实时快照
 * 对应契约文档 section 4.1 GET /api/vehicles/snapshot
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleSnapshot {

    private Long vehicleId;
    private String plateNo;
    private double lng;
    private double lat;
    private double speed;
    private double heading;
    private double battery;
    /** 0=离线 1=在线 */
    private int status;
    private String lastTs;
}
