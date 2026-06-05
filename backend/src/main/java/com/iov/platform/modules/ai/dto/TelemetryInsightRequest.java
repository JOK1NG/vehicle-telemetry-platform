package com.iov.platform.modules.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class TelemetryInsightRequest {

    @NotNull
    private Long vehicleId;

    @NotNull
    private TimeRange timeRange;

    private Map<String, List<Double>> metrics;

    private List<String> alerts;

    @Data
    public static class TimeRange {
        @NotNull
        private String start;
        @NotNull
        private String end;
    }
}
