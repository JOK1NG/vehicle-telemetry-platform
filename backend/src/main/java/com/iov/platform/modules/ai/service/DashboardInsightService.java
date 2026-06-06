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
            AiChatGateway.ChatResult response;
            String textContext;
            if (useImageInput) {
                long screenshotStart = System.currentTimeMillis();
                byte[] imageBytes = getDashboardImageBytes(request, authorizationHeader, user);
                screenshotMs = System.currentTimeMillis() - screenshotStart;

                long contextStart = System.currentTimeMillis();
                textContext = buildContext(request, null, true);
                contextMs = System.currentTimeMillis() - contextStart;

                long modelStart = System.currentTimeMillis();
                response = chatGateway.chat(new AiChatGateway.ChatRequest(
                        model,
                        systemPrompt,
                        textContext,
                        imageBytes,
                        true
                ));
                modelMs = System.currentTimeMillis() - modelStart;
            } else {
                long screenshotStart = System.currentTimeMillis();
                String visibleText = getDashboardVisibleText(request, authorizationHeader, user);
                screenshotMs = System.currentTimeMillis() - screenshotStart;

                long contextStart = System.currentTimeMillis();
                textContext = buildContext(request, visibleText, false);
                contextMs = System.currentTimeMillis() - contextStart;

                long modelStart = System.currentTimeMillis();
                response = chatGateway.chat(new AiChatGateway.ChatRequest(
                        model,
                        systemPrompt,
                        textContext,
                        null,
                        true
                ));
                modelMs = System.currentTimeMillis() - modelStart;
            }

            String result = response.content();
            long latency = System.currentTimeMillis() - start;

            logService.log("dashboard_insight", response.model(), response.provider(),
                    (useImageInput ? "image+" : "text+") + textContext.length() + "chars",
                    truncate(result, 500), true, (int) latency, (int) response.totalTokens(), userId);

            long parseStart = System.currentTimeMillis();
            DashboardInsightResponse parsed = parseResponse(result, latency);
            parseMs = System.currentTimeMillis() - parseStart;
            long totalLatency = System.currentTimeMillis() - start;
            parsed.setLatencyMs(totalLatency);
            parsed.setTiming(timing(screenshotMs, contextMs, modelMs, parseMs, totalLatency, useImageInput));
            return parsed;
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
            return Base64.getDecoder().decode(base64);
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
        String normalized = model != null ? model.toLowerCase() : "";
        boolean imageCapable = normalized.contains("vl")
                || normalized.contains("vision")
                || normalized.contains("omni");
        if ("force".equalsIgnoreCase(imageMode)) return true;
        return imageCapable;
    }

    @SuppressWarnings("unchecked")
    private DashboardInsightResponse parseResponse(String raw, long latencyMs) {
        try {
            String json = raw.trim();
            if (json.startsWith("```json")) json = json.substring(7);
            else if (json.startsWith("```")) json = json.substring(3);
            if (json.endsWith("```")) json = json.substring(0, json.length() - 3);
            json = json.trim();

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
            log.warn("Failed to parse AI dashboard insight response", e);
            return DashboardInsightResponse.builder()
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

    @SuppressWarnings("unchecked")
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
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

}
