package com.iov.platform.modules.alert.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@TableName("alert")
public class Alert {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long vehicleId;
    private String type;        // 'OVERSPEED' | 'LOW_BATTERY' | 'OFFLINE' | 'GEOFENCE_ENTER' | 'GEOFENCE_EXIT'
    private Integer level;       // 1=LOW 2=MEDIUM 3=HIGH 4=CRITICAL
    private String message;
    private Double lng;
    private Double lat;
    private OffsetDateTime occurredAt;
    private Boolean handled;
    private Long ruleId;
    private Long geofenceId;
}
