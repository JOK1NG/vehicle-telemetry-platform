package com.iov.platform.modules.alert.dto;

import com.iov.platform.modules.alert.entity.Alert;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * WebSocket /topic/alerts 推送 + REST 历史告警返回的统一 DTO
 * 对应契约文档 §3.3
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AlertItem {
    private Long id;
    private Long vehicleId;
    private String plateNo;
    private String type;
    private Integer level;        // 1=LOW 2=MEDIUM 3=HIGH 4=CRITICAL
    private String message;
    private Double lng;
    private Double lat;
    private OffsetDateTime occurredAt;
    private Boolean handled;
    private Long ruleId;
    private Long geofenceId;

    public static AlertItem from(Alert a, String plateNo) {
        return new AlertItem(
                a.getId(), a.getVehicleId(), plateNo, a.getType(), a.getLevel(),
                a.getMessage(), a.getLng(), a.getLat(), a.getOccurredAt(), a.getHandled(),
                a.getRuleId(), a.getGeofenceId()
        );
    }
}
