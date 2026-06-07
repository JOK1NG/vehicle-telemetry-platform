package com.iov.platform.modules.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class DashboardInsightRequest {

    private Long vehicleId;

    @Size(max = 10_000_000, message = "图片 Base64 大小不能超过 10MB")
    private String dashboardImageBase64;

    @Size(max = 256, message = "时间范围描述过长")
    private String timeRange;

    private Map<String, Object> summaryStats;
}
