package com.iov.platform.modules.realtime.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * MQTT 遥测消息 JSON payload
 * 对应契约文档 §2.2 Telemetry Payload
 *
 * lng/lat 为必填字段（使用 Double 包装类型，反序列化缺失时为 null 而非 0）
 * speed/heading/battery 为可选字段（缺失时由 RealtimeService 赋默认值）
 */
@Data
public class TelemetryMessage {

    /** ISO 8601 UTC 时间戳 */
    private String ts;

    /** 经度 GCJ-02（必填，null 时拒绝消息） */
    private Double lng;

    /** 纬度 GCJ-02（必填，null 时拒绝消息） */
    private Double lat;

    /** 速度 km/h（可选，null 时默认 0） */
    private Double speed;

    /** 航向角 0-360（可选，null 时默认 0） */
    private Double heading;

    /** 电量 0-100%（可选，null 时默认 0） */
    private Double battery;

    /** 故障码，无故障时 null */
    @JsonProperty("faultCode")
    private String faultCode;
}
