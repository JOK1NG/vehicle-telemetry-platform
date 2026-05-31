package com.iov.platform.modules.realtime.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * MQTT 遥测消息 JSON payload
 * 对应契约文档 §2.2 Telemetry Payload
 */
@Data
public class TelemetryMessage {

    /** ISO 8601 UTC 时间戳 */
    private String ts;

    /** 经度 GCJ-02 */
    private double lng;

    /** 纬度 GCJ-02 */
    private double lat;

    /** 速度 km/h */
    private double speed;

    /** 航向角 0-360 */
    private double heading;

    /** 电量 0-100% */
    private double battery;

    /** 故障码，无故障时 null */
    @JsonProperty("faultCode")
    private String faultCode;
}
