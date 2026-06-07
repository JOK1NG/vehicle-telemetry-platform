package com.iov.platform.modules.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.modules.ai.dto.DashboardInsightRequest;
import com.iov.platform.modules.ai.dto.DashboardInsightResponse;
import com.iov.platform.modules.auth.service.AuthUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardInsightService {

    private static final List<String> VALID_SEVERITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final int MAX_ANALYZE_ATTEMPTS = 3;

    private final PromptTemplateService promptService;
    private final AiCallLogService logService;
    private final ObjectMapper objectMapper;
    private final DashboardScreenshotService screenshotService;
    private final AiChatGateway chatGateway;

    @Value("${ai.dashboard-insight.image-mode:auto}")
    private String imageMode;

    public DashboardInsightResponse analyze(
            DashboardInsightRequest request,
            Long userId,
            String authorizationHeader,
            AuthUserDetails user) {
        String systemPrompt = promptService.getSystemPrompt("dashboard_insight");
        String model = chatGateway.getDefaultModel();
        boolean useImageInput = shouldUseImageInput(model);
        long start = System.currentTimeMillis();
        long screenshotMs = 0;
        long contextMs = 0;
        long modelMs = 0;
        long parseMs = 0;

        try {
            String textContext;
            byte[] imageBytes = null;
            if (useImageInput) {
                long screenshotStart = System.currentTimeMillis();
                imageBytes = getDashboardImageBytes(request, authorizationHeader, user);
                screenshotMs = System.currentTimeMillis() - screenshotStart;

                long contextStart = System.currentTimeMillis();
                textContext = buildContext(request, null, true);
                contextMs = System.currentTimeMillis() - contextStart;
            } else {
                long screenshotStart = System.currentTimeMillis();
                String visibleText = getDashboardVisibleText(request, authorizationHeader, user);
                screenshotMs = System.currentTimeMillis() - screenshotStart;

                long contextStart = System.currentTimeMillis();
                textContext = buildContext(request, visibleText, false);
                contextMs = System.currentTimeMillis() - contextStart;
            }

            ValidationIssue retryIssue = ValidationIssue.INVALID_SCHEMA;
            String previousOutput = "";
            for (int attempt = 1; attempt <= MAX_ANALYZE_ATTEMPTS; attempt++) {
                String userPayload = attempt == 1
                        ? textContext
                        : retryPrompt(textContext, retryIssue, previousOutput, useImageInput);
                long modelStart = System.currentTimeMillis();
                AiChatGateway.ChatResult response = chatGateway.chat(new AiChatGateway.ChatRequest(
                        model, systemPrompt, userPayload, imageBytes, true));
                modelMs = System.currentTimeMillis() - modelStart;

                String result = response.content();
                long latency = System.currentTimeMillis() - start;

                long parseStart = System.currentTimeMillis();
                DashboardInsightResponse parsed = parseResponse(result, latency);
                parseMs = System.currentTimeMillis() - parseStart;
                ValidationIssue issue = validationIssue(parsed, result);
                boolean valid = issue == ValidationIssue.NONE;

                logService.log("dashboard_insight", response.model(), response.provider(),
                        "attempt=" + attempt + "; issue=" + issue.label() + "; "
                                + (useImageInput ? "image+" : "text+") + userPayload.length() + "chars",
                        truncate(result, 500), valid, (int) latency, (int) response.totalTokens(), userId);

                if (valid) {
                    long totalLatency = System.currentTimeMillis() - start;
                    parsed.setLatencyMs(totalLatency);
                    parsed.setTiming(timing(screenshotMs, contextMs, modelMs, parseMs, totalLatency, useImageInput));
                    return parsed;
                }

                retryIssue = issue;
                previousOutput = result;
            }

            String message = "AI dashboard insight did not produce valid JSON after "
                    + MAX_ANALYZE_ATTEMPTS + " attempts; lastIssue=" + retryIssue.label();
            throw new IllegalStateException(message);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.error("Dashboard insight failed", e);
            return DashboardInsightResponse.builder()
                    .summary("分析失败: " + e.getMessage())
                    .severity("UNKNOWN")
                    .findings(List.of())
                    .recommendations(List.of())
                    .latencyMs(latency)
                    .timing(timing(screenshotMs, contextMs, modelMs, parseMs, latency, useImageInput))
                    .build();
        }
    }

    private byte[] getDashboardImageBytes(
            DashboardInsightRequest request,
            String authorizationHeader,
            AuthUserDetails user) {
        String base64 = request.getDashboardImageBase64();
        if (base64 != null && !base64.isBlank()) {
            if (base64.contains(",")) {
                base64 = base64.substring(base64.indexOf(",") + 1);
            }
            try {
                return Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid base64 dashboard image data", e);
            }
        }
        return screenshotService.captureDashboardPng(authorizationHeader, user);
    }

    private String getDashboardVisibleText(
            DashboardInsightRequest request,
            String authorizationHeader,
            AuthUserDetails user) {
        if (request.getDashboardImageBase64() != null && !request.getDashboardImageBase64().isBlank()) {
            return "当前模型未启用图片输入，已忽略前端提供的图片。";
        }
        return screenshotService.captureDashboardSnapshot(authorizationHeader, user, false).visibleText();
    }

    private String buildContext(DashboardInsightRequest request, String visibleText, boolean includesImage) {
        StringBuilder sb = new StringBuilder();
        if (includesImage) {
            sb.append("请分析以下车联网监控大屏截图。\n");
        } else {
            sb.append("请分析以下车联网监控大屏页面文本快照。当前模型未启用图片输入，因此不要声称看到了截图像素细节。\n");
        }

        if (request.getVehicleId() != null) {
            sb.append("当前选中车辆ID: ").append(request.getVehicleId()).append("\n");
        }
        if (request.getTimeRange() != null) {
            sb.append("时间范围: ").append(request.getTimeRange()).append("\n");
        }

        if (request.getSummaryStats() != null && !request.getSummaryStats().isEmpty()) {
            sb.append("当前统计摘要:\n");
            request.getSummaryStats().forEach((k, v) ->
                    sb.append("  ").append(k).append(": ").append(v).append("\n"));
        }

        if (!includesImage && StringUtils.hasText(visibleText)) {
            sb.append("\n页面可见文本:\n");
            sb.append(truncate(visibleText, 4_000)).append("\n");
        }

        if (includesImage) {
            sb.append("\n请观察截图中的曲线、地图轨迹、告警列表和统计卡片，给出综合分析。");
        } else {
            sb.append("\n请基于页面文本和统计摘要给出综合分析；对曲线形态、地图轨迹等视觉细节只能标记为无法从文本确认。");
        }
        return sb.toString();
    }

    private boolean shouldUseImageInput(String model) {
        if ("false".equalsIgnoreCase(imageMode)) return false;
        if ("text".equalsIgnoreCase(imageMode)) return false;
        if ("true".equalsIgnoreCase(imageMode)) return true;
        if ("image".equalsIgnoreCase(imageMode)) return true;
        if ("vision".equalsIgnoreCase(imageMode)) return true;
        String normalized = model != null ? model.toLowerCase() : "";
        boolean imageCapable = normalized.contains("vl")
                || normalized.contains("vision")
                || normalized.contains("omni");
        if ("force".equalsIgnoreCase(imageMode)) return true;
        return imageCapable;
    }

    private DashboardInsightResponse parseResponse(String raw, long latencyMs) {
        if (!StringUtils.hasText(raw)) {
            return invalidResponse(latencyMs);
        }
        try {
            String json = extractJsonObject(raw);

            Map<String, Object> parsed = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
            List<DashboardInsightResponse.DashboardFinding> findings = findingListField(parsed, "findings");
            List<String> recommendations = stringListField(parsed, "recommendations");
            String summary = stringField(parsed, "summary");

            return DashboardInsightResponse.builder()
                    .summary(summaryOrFallback(summary, findings))
                    .severity(stringField(parsed, "severity"))
                    .findings(findings)
                    .recommendations(recommendations)
                    .latencyMs(latencyMs)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse AI dashboard insight response: {}", e.getMessage());
            return invalidResponse(latencyMs);
        }
    }

    private String stringField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }

    private List<String> stringListField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    private List<DashboardInsightResponse.DashboardFinding> findingListField(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (!(v instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(this::toFinding)
                .toList();
    }

    private DashboardInsightResponse.DashboardFinding toFinding(Object item) {
        if (item instanceof Map<?, ?> rawMap) {
            Map<String, Object> finding = new LinkedHashMap<>();
            rawMap.forEach((k, v) -> finding.put(String.valueOf(k), v));
            return DashboardInsightResponse.DashboardFinding.builder()
                    .type(stringField(finding, "type"))
                    .description(stringField(finding, "description"))
                    .detail(stringField(finding, "detail"))
                    .build();
        }
        return DashboardInsightResponse.DashboardFinding.builder()
                .type("OBSERVATION")
                .description(item != null ? item.toString() : "")
                .detail("")
                .build();
    }

    private String summaryOrFallback(
            String summary,
            List<DashboardInsightResponse.DashboardFinding> findings) {
        if (StringUtils.hasText(summary)) {
            return summary;
        }
        if (findings != null && !findings.isEmpty()) {
            return "发现 " + findings.size() + " 项大屏现象：" + findings.get(0).getDescription();
        }
        return "AI 返回了结构化诊断，但未提供摘要。";
    }

    private ValidationIssue validationIssue(DashboardInsightResponse response, String raw) {
        if (!StringUtils.hasText(raw)) {
            return ValidationIssue.EMPTY_OUTPUT;
        }
        if (AiJsonUtils.looksTruncatedJson(raw)) {
            return ValidationIssue.TRUNCATED_JSON;
        }
        if (response == null || !StringUtils.hasText(response.getSummary())) {
            return ValidationIssue.INVALID_SCHEMA;
        }
        if (response.getSummary().trim().startsWith("{")) {
            return ValidationIssue.INVALID_SCHEMA;
        }
        if (!VALID_SEVERITIES.contains(response.getSeverity())) {
            return ValidationIssue.UNKNOWN_SEVERITY;
        }
        if (response.getFindings() == null || response.getFindings().isEmpty()) {
            return ValidationIssue.INVALID_SCHEMA;
        }
        if (response.getRecommendations() == null || response.getRecommendations().isEmpty()) {
            return ValidationIssue.INVALID_SCHEMA;
        }
        return ValidationIssue.NONE;
    }

    private String retryPrompt(
            String context,
            ValidationIssue issue,
            String previousOutput,
            boolean includesImage) {
        return """
                修正上一次输出。问题：%s。
                禁止 markdown，禁止解释过程，禁止把 JSON 当字符串输出。
                不要继续上文，重新输出一个完整 JSON 对象：
                {"summary":"...","severity":"LOW|MEDIUM|HIGH|CRITICAL","findings":[{"type":"...","description":"...","detail":"..."}],"recommendations":["..."]}
                findings 和 recommendations 各 2-4 条，必须闭合所有数组和对象。

                上一次输出片段：
                %s

                大屏上下文：
                %s
                """.formatted(issue.label(), truncate(previousOutput, 400), context
                + (includesImage ? "\n本次仍附带同一张大屏截图。" : ""));
    }

    private String extractJsonObject(String raw) {
        String json = AiJsonUtils.stripFence(raw);
        int start = json.indexOf('{');
        int end = json.lastIndexOf('}');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        return json.trim();
    }

    private DashboardInsightResponse invalidResponse(long latencyMs) {
        return DashboardInsightResponse.builder()
                .summary("")
                .severity("UNKNOWN")
                .findings(List.of())
                .recommendations(List.of())
                .latencyMs(latencyMs)
                .build();
    }

    private static DashboardInsightResponse.Timing timing(
            long screenshotMs,
            long contextMs,
            long modelMs,
            long parseMs,
            long totalMs,
            boolean imageInput) {
        return DashboardInsightResponse.Timing.builder()
                .screenshotMs(screenshotMs)
                .contextMs(contextMs)
                .modelMs(modelMs)
                .parseMs(parseMs)
                .totalMs(totalMs)
                .imageInput(imageInput)
                .build();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private enum ValidationIssue {
        NONE("none"),
        EMPTY_OUTPUT("空内容"),
        TRUNCATED_JSON("JSON 截断或未闭合"),
        UNKNOWN_SEVERITY("severity 缺失或非法"),
        INVALID_SCHEMA("schema 字段缺失或类型不正确");

        private final String label;

        ValidationIssue(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

}
