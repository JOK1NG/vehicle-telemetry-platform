package com.iov.platform.modules.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.modules.ai.dto.TelemetryInsightRequest;
import com.iov.platform.modules.ai.dto.TelemetryInsightResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryInsightService {

    private final JdbcTemplate jdbcTemplate;
    private final ChatOrchestratorService orchestrator;
    private final PromptTemplateService promptService;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.chat.options.model:qwen3.7-plus}")
    private String model;

    public TelemetryInsightResponse analyze(TelemetryInsightRequest request, Long userId) {
        String contextJson = buildContext(request);
        String systemPrompt = promptService.getSystemPrompt("telemetry_insight");

        long start = System.currentTimeMillis();
        String result = orchestrator.chat(
                "telemetry_insight",
                model,
                "qwen",
                systemPrompt,
                contextJson,
                userId
        );
        long latency = System.currentTimeMillis() - start;

        return parseResponse(result, latency);
    }

    private String buildContext(TelemetryInsightRequest request) {
        Map<String, Object> ctx = new LinkedHashMap<>();

        ctx.put("vehicleId", request.getVehicleId());
        ctx.put("timeRange", request.getTimeRange());

        if (request.getMetrics() != null && !request.getMetrics().isEmpty()) {
            ctx.put("metrics", request.getMetrics());
        } else {
            ctx.put("telemetry", queryTelemetry(request.getVehicleId(),
                    request.getTimeRange().getStart(),
                    request.getTimeRange().getEnd()));
        }

        if (request.getAlerts() != null && !request.getAlerts().isEmpty()) {
            ctx.put("alerts", request.getAlerts());
        }

        try {
            return objectMapper.writeValueAsString(ctx);
        } catch (Exception e) {
            log.warn("Failed to serialize telemetry context, falling back to toString", e);
            return ctx.toString();
        }
    }

    private Map<String, List<Double>> queryTelemetry(Long vehicleId, String start, String end) {
        Map<String, List<Double>> metrics = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT speed, heading, battery FROM telemetry "
                  + "WHERE vehicle_id = ? AND time BETWEEN ?::timestamptz AND ?::timestamptz "
                  + "ORDER BY time ASC LIMIT 200",
                    vehicleId, start, end
            );

            for (Map<String, Object> row : rows) {
                addMetric(metrics, "speed", row.get("speed"));
                addMetric(metrics, "heading", row.get("heading"));
                addMetric(metrics, "battery", row.get("battery"));
            }
        } catch (Exception e) {
            log.warn("Query telemetry failed for vehicleId={}: {}", vehicleId, e.getMessage());
        }
        return metrics;
    }

    private void addMetric(Map<String, List<Double>> metrics, String key, Object val) {
        if (val instanceof Number n) {
            metrics.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(n.doubleValue());
        }
    }

    private TelemetryInsightResponse parseResponse(String raw, long latencyMs) {
        try {
            // Strip possible markdown code fences
            String json = raw.trim();
            if (json.startsWith("```json")) {
                json = json.substring(7);
            } else if (json.startsWith("```")) {
                json = json.substring(3);
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3);
            }
            json = json.trim();

            Map<String, Object> parsed = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});

            return TelemetryInsightResponse.builder()
                    .summary(stringField(parsed, "summary"))
                    .severity(stringField(parsed, "severity"))
                    .findings(stringListField(parsed, "findings"))
                    .recommendations(stringListField(parsed, "recommendations"))
                    .latencyMs(latencyMs)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse AI insight response, returning raw text. error={}", e.getMessage());
            return TelemetryInsightResponse.builder()
                    .summary(raw)
                    .severity("UNKNOWN")
                    .findings(List.of())
                    .recommendations(List.of())
                    .latencyMs(latencyMs)
                    .build();
        }
    }

    @SuppressWarnings("unchecked")
    private String stringField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private List<String> stringListField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }
}
