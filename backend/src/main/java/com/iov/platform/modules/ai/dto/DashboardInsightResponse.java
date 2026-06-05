package com.iov.platform.modules.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardInsightResponse {

    private String summary;
    private String severity;
    private List<DashboardFinding> findings;
    private List<String> recommendations;
    private long latencyMs;
    private Timing timing;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DashboardFinding {
        private String type;
        private String description;
        private String detail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Timing {
        private long screenshotMs;
        private long contextMs;
        private long modelMs;
        private long parseMs;
        private long totalMs;
        private boolean imageInput;
    }
}
