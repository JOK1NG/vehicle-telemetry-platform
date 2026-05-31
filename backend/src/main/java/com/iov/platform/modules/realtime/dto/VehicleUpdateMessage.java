package com.iov.platform.modules.realtime.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * WebSocket /topic/vehicles 广播中的单条车辆信息
 * 对应契约文档 section 3.2
 */
@Data
@AllArgsConstructor
public class VehicleUpdateMessage {

    private Long vehicleId;
    private String plateNo;
    private double lng;
    private double lat;
    private double speed;
    private double heading;
    private double battery;
    /** 0=离线 1=在线 */
    private int status;
}
