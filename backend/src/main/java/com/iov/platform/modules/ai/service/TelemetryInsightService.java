package com.iov.platform.modules.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.modules.ai.dto.TelemetryInsightRequest;
import com.iov.platform.modules.ai.dto.TelemetryInsightResponse;
import com.iov.platform.modules.ai.dto.TelemetryInsightStreamEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelemetryInsightService {

    private static final List<String> VALID_SEVERITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    private final JdbcTemplate jdbcTemplate;
    private final AiChatGateway chatGateway;
    private final PromptTemplateService promptService;
    private final ObjectMapper objectMapper;
    private final AiCallLogService logService;

    public TelemetryInsightResponse analyze(TelemetryInsightRequest request, Long userId) {
        String contextJson = buildContext(request);
        String systemPrompt = promptService.getSystemPrompt("telemetry_insight");
        String model = chatGateway.getDefaultModel();
        String provider = chatGateway.getProvider();

        TelemetryInsightResponse lastParsed = null;
        Exception lastException = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            String userPayload = attempt == 1 ? contextJson : retryPrompt(contextJson);
            try {
                AiChatGateway.ChatResult callResult = chatGateway.chat(new AiChatGateway.ChatRequest(
                        model,
                        systemPrompt,
                        userPayload,
                        null,
                        true
                ));
                TelemetryInsightResponse parsed = parseResponse(callResult.content(), callResult.latencyMs());
                boolean valid = isValid(parsed);
                logService.log("telemetry_insight", model, provider,
                        "attempt=" + attempt + "; " + reqSummary(systemPrompt, userPayload),
                        truncate(callResult.content(), 500),
                        valid, (int) callResult.latencyMs(), (int) callResult.totalTokens(), userId);
                if (valid) {
                    return parsed;
                }
                lastParsed = parsed;
            } catch (Exception e) {
                lastException = e;
                logService.log("telemetry_insight", model, provider,
                        "attempt=" + attempt + "; " + reqSummary(systemPrompt, userPayload),
                        "ERROR: " + e.getMessage(),
                        false, null, null, userId);
            }
        }
        if (lastParsed != null) {
            return lastParsed;
        }
        throw new IllegalStateException("AI telemetry insight failed after retry", lastException);
    }

    public Flux<TelemetryInsightStreamEvent> streamAnalyze(TelemetryInsightRequest request, Long userId) {
        String contextJson = buildContext(request);
        String systemPrompt = promptService.getSystemPrompt("telemetry_insight");
        String model = chatGateway.getDefaultModel();
        String provider = chatGateway.getProvider();
        StringBuilder raw = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        String requestSummary = reqSummary(systemPrompt, contextJson);

        return Flux.defer(() -> chatGateway.stream(new AiChatGateway.ChatRequest(
                        model,
                        systemPrompt,
                        contextJson,
                        null,
                        true
                )))
                .doOnNext(raw::append)
                .map(TelemetryInsightStreamEvent::delta)
                .concatWith(Mono.fromSupplier(() -> {
                    long latencyMs = System.currentTimeMillis() - startedAt;
                    TelemetryInsightResponse parsed = parseResponse(raw.toString(), latencyMs);
                    boolean valid = isValid(parsed);
                    logService.log("telemetry_insight_stream", model, provider,
                            requestSummary,
                            truncate(raw.toString(), 500),
                            valid, (int) latencyMs, null, userId);
                    if (!valid) {
                        return TelemetryInsightStreamEvent.error(
                                "AI 流式输出未满足接口 JSON schema，请重试。",
                                latencyMs
                        );
                    }
                    return TelemetryInsightStreamEvent.finalResult(parsed, latencyMs);
                }))
                .onErrorResume(e -> {
                    long latencyMs = System.currentTimeMillis() - startedAt;
                    logService.log("telemetry_insight_stream", model, provider,
                            requestSummary,
                            "ERROR: " + e.getMessage(),
                            false, (int) latencyMs, null, userId);
                    return Flux.just(TelemetryInsightStreamEvent.error(
                            "AI 流式诊断失败：" + e.getMessage(),
                            latencyMs
                    ));
                });
    }

    private static String reqSummary(String system, String user) {
        return "system=" + truncate(system, 500) + "; user=" + truncate(user, 500);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
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
            List<String> findings = stringListField(parsed, "findings");
            List<String> recommendations = stringListField(parsed, "recommendations");
            String summary = stringField(parsed, "summary");
            String severity = normalizeSeverity(stringField(parsed, "severity"));

            return TelemetryInsightResponse.builder()
                    .summary(summaryOrFallback(summary, findings))
                    .severity(severity)
                    .findings(findings)
                    .recommendations(recommendations)
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
            return list.stream()
                    .map(this::findingToString)
                    .filter(s -> s != null && !s.isBlank())
                    .toList();
        }
        return List.of();
    }

    private String findingToString(Object item) {
        if (item == null) return null;
        if (item instanceof String s) return s;
        if (item instanceof Map<?, ?> m) {
            Object desc = m.get("description");
            if (desc != null) {
                Object comp = m.get("component");
                if (comp != null && !comp.toString().isBlank()) {
                    return "[" + comp + "] " + desc;
                }
                return desc.toString();
            }
            return m.toString();
        }
        return item.toString();
    }

    private String retryPrompt(String contextJson) {
        return """
                上一次输出未满足接口 JSON schema。请重新分析下面的车辆遥测上下文。
                禁止复述输入，禁止输出 markdown，禁止省略 summary。
                只能输出一个 JSON 对象：
                {"summary":"...","severity":"LOW|MEDIUM|HIGH|CRITICAL","findings":["..."],"recommendations":["..."]}

                车辆遥测上下文：
                """ + contextJson;
    }

    private boolean isValid(TelemetryInsightResponse response) {
        return response != null
                && StringUtils.hasText(response.getSummary())
                && VALID_SEVERITIES.contains(response.getSeverity());
    }

    private String normalizeSeverity(String severity) {
        if (!StringUtils.hasText(severity)) {
            return "UNKNOWN";
        }
        String normalized = severity.trim().toUpperCase();
        return VALID_SEVERITIES.contains(normalized) ? normalized : "UNKNOWN";
    }

    private String summaryOrFallback(String summary, List<String> findings) {
        if (StringUtils.hasText(summary)) {
            return summary;
        }
        if (findings != null && !findings.isEmpty()) {
            return "发现 " + findings.size() + " 项遥测现象：" + findings.get(0);
        }
        return "AI 返回了结构化诊断，但未提供摘要。";
    }
}
