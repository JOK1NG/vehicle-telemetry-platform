package com.iov.platform.modules.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryInsightStreamEvent {

    private String type;
    private String delta;
    private TelemetryInsightResponse result;
    private String error;
    private long elapsedMs;

    public static TelemetryInsightStreamEvent delta(String delta) {
        return TelemetryInsightStreamEvent.builder()
                .type("delta")
                .delta(delta)
                .build();
    }

    public static TelemetryInsightStreamEvent finalResult(TelemetryInsightResponse result, long elapsedMs) {
        return TelemetryInsightStreamEvent.builder()
                .type("final")
                .result(result)
                .elapsedMs(elapsedMs)
                .build();
    }

    public static TelemetryInsightStreamEvent error(String error, long elapsedMs) {
        return TelemetryInsightStreamEvent.builder()
                .type("error")
                .error(error)
                .elapsedMs(elapsedMs)
                .build();
    }
}
