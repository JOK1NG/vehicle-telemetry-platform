package com.iov.platform.modules.realtime.entity;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 遥测时序表实体
 * 对应数据库 telemetry 表（Hypertable，无自增主键）
 * 注意：speed/heading/battery 使用 Double 以保持与 DTO 和 DB REAL 类型一致
 */
@Data
public class Telemetry {

    private OffsetDateTime time;
    private Long vehicleId;
    private double lng;
    private double lat;
    private Double speed;
    private Double heading;
    private Double battery;
    private String faultCode;
}
