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
    private static final int MAX_ANALYZE_ATTEMPTS = 3;
    private static final int TELEMETRY_QUERY_LIMIT = 200;

    private final JdbcTemplate jdbcTemplate;
    private final AiChatGateway chatGateway;
    private final PromptTemplateService promptService;
    private final ObjectMapper objectMapper;
    private final AiCallLogService logService;

    public TelemetryInsightResponse analyze(TelemetryInsightRequest request, Long userId) {
        long startedAt = System.currentTimeMillis();
        TelemetryContext context = buildContext(request);
        String contextJson = context.json();
        String systemPrompt = promptService.getSystemPrompt("telemetry_insight");
        String model = chatGateway.getDefaultModel();
        String provider = chatGateway.getProvider();

        if (!context.hasDiagnosticSignals()) {
            int latencyMs = (int) (System.currentTimeMillis() - startedAt);
            TelemetryInsightResponse response = noTelemetryResponse(latencyMs);
            logService.log("telemetry_insight", model, provider,
                    "skipped=no_telemetry; " + reqSummary(systemPrompt, contextJson),
                    response.getSummary(),
                    true, latencyMs, 0, userId);
            return response;
        }

        ValidationIssue retryIssue = ValidationIssue.INVALID_SCHEMA;
        String previousOutput = "";
        for (int attempt = 1; attempt <= MAX_ANALYZE_ATTEMPTS; attempt++) {
            String userPayload = attempt == 1 ? contextJson : retryPrompt(contextJson, retryIssue, previousOutput);
            try {
                AiChatGateway.ChatResult callResult = chatGateway.chat(new AiChatGateway.ChatRequest(
                        model,
                        systemPrompt,
                        userPayload,
                        null,
                        true
                ));
                TelemetryInsightResponse parsed = parseResponse(callResult.content(), callResult.latencyMs());
                ValidationIssue issue = validationIssue(parsed, callResult.content());
                boolean valid = issue == ValidationIssue.NONE;
                logService.log("telemetry_insight", model, provider,
                        "attempt=" + attempt + "; issue=" + issue.label() + "; "
                                + reqSummary(systemPrompt, userPayload),
                        truncate(callResult.content(), 500),
                        valid, (int) callResult.latencyMs(), (int) callResult.totalTokens(), userId);
                if (valid) {
                    return parsed;
                }
                retryIssue = issue;
                previousOutput = callResult.content();
            } catch (Exception e) {
                retryIssue = ValidationIssue.MODEL_ERROR;
                previousOutput = e.getMessage();
                logService.log("telemetry_insight", model, provider,
                        "attempt=" + attempt + "; " + reqSummary(systemPrompt, userPayload),
                        "ERROR: " + e.getMessage(),
                        false, null, null, userId);
            }
        }
        return TelemetryInsightResponse.builder()
                .summary("AI 诊断未能生成有效结果（" + retryIssue.label() + "），请重试。")
                .severity("UNKNOWN")
                .findings(List.of())
                .recommendations(List.of("请稍后重试 AI 诊断。"))
                .latencyMs(System.currentTimeMillis() - startedAt)
                .build();
    }

    public Flux<TelemetryInsightStreamEvent> streamAnalyze(TelemetryInsightRequest request, Long userId) {
        TelemetryContext context = buildContext(request);
        String contextJson = context.json();
        String systemPrompt = promptService.getSystemPrompt("telemetry_insight");
        String model = chatGateway.getDefaultModel();
        String provider = chatGateway.getProvider();
        StringBuilder raw = new StringBuilder();
        long startedAt = System.currentTimeMillis();
        String requestSummary = reqSummary(systemPrompt, contextJson);

        if (!context.hasDiagnosticSignals()) {
            int latencyMs = (int) (System.currentTimeMillis() - startedAt);
            TelemetryInsightResponse response = noTelemetryResponse(latencyMs);
            logService.log("telemetry_insight_stream", model, provider,
                    "skipped=no_telemetry; " + requestSummary,
                    response.getSummary(),
                    true, latencyMs, 0, userId);
            return Flux.just(TelemetryInsightStreamEvent.finalResult(response, latencyMs));
        }

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
                    ValidationIssue issue = validationIssue(parsed, raw.toString());
                    boolean valid = issue == ValidationIssue.NONE;
                    logService.log("telemetry_insight_stream", model, provider,
                            requestSummary,
                            truncate(raw.toString(), 500),
                            valid, (int) latencyMs, null, userId);
                    if (!valid) {
                        return TelemetryInsightStreamEvent.error(
                                "AI 流式输出未满足接口 JSON schema（" + issue.label() + "），请重试。",
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

    private TelemetryContext buildContext(TelemetryInsightRequest request) {
        Map<String, Object> ctx = new LinkedHashMap<>();

        ctx.put("vehicleId", request.getVehicleId());
        ctx.put("timeRange", request.getTimeRange());

        Map<String, List<Double>> metrics;
        if (request.getMetrics() != null && !request.getMetrics().isEmpty()) {
            metrics = request.getMetrics();
            ctx.put("telemetrySource", "request.metrics");
        } else {
            metrics = queryTelemetry(request.getVehicleId(),
                    request.getTimeRange().getStart(),
                    request.getTimeRange().getEnd());
            ctx.put("telemetrySource", "database.telemetry");
        }
        ctx.put("telemetrySampleLimit", TELEMETRY_QUERY_LIMIT);
        Map<String, Object> metricSummary = summarizeMetrics(metrics);
        ctx.put("metricSummary", metricSummary);

        if (request.getAlerts() != null && !request.getAlerts().isEmpty()) {
            ctx.put("alerts", request.getAlerts());
        } else {
            ctx.put("alerts", List.of());
        }

        try {
            return new TelemetryContext(
                    objectMapper.writeValueAsString(ctx),
                    !metricSummary.isEmpty(),
                    request.getAlerts() != null && !request.getAlerts().isEmpty()
            );
        } catch (Exception e) {
            log.warn("Failed to serialize telemetry context, falling back to toString", e);
            return new TelemetryContext(
                    ctx.toString(),
                    !metricSummary.isEmpty(),
                    request.getAlerts() != null && !request.getAlerts().isEmpty()
            );
        }
    }

    private Map<String, List<Double>> queryTelemetry(Long vehicleId, String start, String end) {
        Map<String, List<Double>> metrics = new LinkedHashMap<>();
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT speed, heading, battery FROM telemetry "
                  + "WHERE vehicle_id = ? AND time BETWEEN ?::timestamptz AND ?::timestamptz "
                  + "ORDER BY time ASC LIMIT ?",
                    vehicleId, start, end, TELEMETRY_QUERY_LIMIT
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

    private Map<String, Object> summarizeMetrics(Map<String, List<Double>> metrics) {
        Map<String, Object> summaries = new LinkedHashMap<>();
        if (metrics == null || metrics.isEmpty()) {
            return summaries;
        }
        metrics.forEach((key, values) -> {
            Map<String, Object> summary = summarizeMetric(values);
            if (!summary.isEmpty()) {
                summaries.put(key, summary);
            }
        });
        return summaries;
    }

    private Map<String, Object> summarizeMetric(List<Double> values) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            return summary;
        }
        List<Double> cleaned = values.stream()
                .filter(v -> v != null && !v.isNaN() && !v.isInfinite())
                .toList();
        if (cleaned.isEmpty()) {
            return summary;
        }

        double min = cleaned.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = cleaned.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double avg = cleaned.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double first = cleaned.get(0);
        double last = cleaned.get(cleaned.size() - 1);
        double delta = last - first;

        summary.put("count", cleaned.size());
        summary.put("min", roundMetric(min));
        summary.put("max", roundMetric(max));
        summary.put("avg", roundMetric(avg));
        summary.put("first", roundMetric(first));
        summary.put("last", roundMetric(last));
        summary.put("delta", roundMetric(delta));
        summary.put("trend", trend(delta));
        return summary;
    }

    private void addMetric(Map<String, List<Double>> metrics, String key, Object val) {
        if (val instanceof Number n) {
            metrics.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(n.doubleValue());
        }
    }

    private TelemetryInsightResponse parseResponse(String raw, long latencyMs) {
        try {
            String json = AiJsonUtils.stripFence(raw);
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

    private String stringField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }


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

    private String retryPrompt(String contextJson, ValidationIssue issue, String previousOutput) {
        return """
                修正上一次输出。问题：%s。
                禁止复述输入，禁止 markdown，禁止解释过程。
                不要继续上文，重新输出一个完整 JSON：
                {"summary":"...","severity":"LOW|MEDIUM|HIGH|CRITICAL","findings":["..."],"recommendations":["..."]}
                findings 和 recommendations 各 2-4 条短句。

                上一次输出片段：
                %s

                遥测摘要：
                %s
                """.formatted(issue.label(), truncate(previousOutput, 300), contextJson);
    }

    private ValidationIssue validationIssue(TelemetryInsightResponse response, String raw) {
        if (!StringUtils.hasText(raw)) {
            return ValidationIssue.EMPTY_OUTPUT;
        }
        if (AiJsonUtils.looksTruncatedJson(raw)) {
            return ValidationIssue.TRUNCATED_JSON;
        }
        if (looksLikeEcho(raw)) {
            return ValidationIssue.ECHOED_INPUT;
        }
        if (response == null || !StringUtils.hasText(response.getSummary())) {
            return ValidationIssue.INVALID_SCHEMA;
        }
        if (!VALID_SEVERITIES.contains(response.getSeverity())) {
            return ValidationIssue.UNKNOWN_SEVERITY;
        }
        return ValidationIssue.NONE;
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

    private boolean looksLikeEcho(String raw) {
        String json = raw.trim();
        return json.startsWith("{")
                && json.contains("\"vehicleId\"")
                && !json.contains("\"severity\"")
                && !json.contains("\"findings\"");
    }

    private double roundMetric(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String trend(double delta) {
        if (Math.abs(delta) < 0.01) {
            return "flat";
        }
        return delta > 0 ? "up" : "down";
    }

    private TelemetryInsightResponse noTelemetryResponse(long latencyMs) {
        return TelemetryInsightResponse.builder()
                .summary("当前时间窗口内没有可用遥测样本或告警，无法进行故障诊断。")
                .severity("LOW")
                .findings(List.of("未查询到 speed、heading、battery 等遥测样本。"))
                .recommendations(List.of("扩大时间窗口后重新分析。", "确认车辆遥测采集链路是否正常。"))
                .latencyMs(latencyMs)
                .build();
    }

    private record TelemetryContext(String json, boolean hasMetrics, boolean hasAlerts) {
        boolean hasDiagnosticSignals() {
            return hasMetrics || hasAlerts;
        }
    }

    private enum ValidationIssue {
        NONE("none"),
        EMPTY_OUTPUT("空内容"),
        TRUNCATED_JSON("JSON 截断或未闭合"),
        ECHOED_INPUT("复述了输入字段"),
        UNKNOWN_SEVERITY("severity 缺失或非法"),
        INVALID_SCHEMA("schema 字段缺失或类型不正确"),
        MODEL_ERROR("模型调用异常");

        private final String label;

        ValidationIssue(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }
}
