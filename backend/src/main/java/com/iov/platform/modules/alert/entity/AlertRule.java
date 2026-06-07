package com.iov.platform.modules.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("alert_rule")
public class AlertRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String code;          // 'OVERSPEED' | 'LOW_BATTERY' | 'OFFLINE' | 'GEOFENCE_ENTER' | 'GEOFENCE_EXIT'
    private String name;
    private Integer level;         // 1=LOW 2=MEDIUM 3=HIGH 4=CRITICAL
    private String metric;         // 'speed' | 'battery' | 'offline_minutes' | 'geofence'
    private String comparator;     // 'GT' | 'LT' | 'EQ'
    private Double threshold;
    private Boolean enabled;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
