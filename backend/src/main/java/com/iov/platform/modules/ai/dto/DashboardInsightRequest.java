package com.iov.platform.modules.ai.dto;

import lombok.Data;

import java.util.Map;

@Data
public class DashboardInsightRequest {

    private Long vehicleId;

    private String dashboardImageBase64;

    private String timeRange;

    private Map<String, Object> summaryStats;
}
