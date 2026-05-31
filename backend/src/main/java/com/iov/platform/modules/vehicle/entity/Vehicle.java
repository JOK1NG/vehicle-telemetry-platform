package com.iov.platform.modules.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 车辆台账实体 (M0 骨架，仅结构演示)
 * 对应数据库 vehicle 表（见设计文档）
 */
@Data
@TableName("vehicle")
public class Vehicle {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String plateNo;

    private String vin;

    private String model;

    private Integer status; // 0离线 1在线

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
