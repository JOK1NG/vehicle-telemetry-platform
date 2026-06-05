package com.iov.platform.modules.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.modules.ai.dto.DashboardInsightRequest;
import com.iov.platform.modules.ai.dto.DashboardInsightResponse;
import com.iov.platform.modules.auth.service.AuthUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardInsightService {

    private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final PromptTemplateService promptService;
    private final AiCallLogService logService;
    private final ObjectMapper objectMapper;
    private final DashboardScreenshotService screenshotService;
    private final RestClient.Builder restClientBuilder;

    @Value("${spring.ai.openai.chat.options.model:qwen3.7-plus}")
    private String chatModel;

    @Value("${spring.ai.openai.api-key:}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url:}")
    private String qwenBaseUrl;

    @Value("${ai.vision.model:qwen3.7-plus}")
    private String visionModel;

    @Value("${ai.dashboard-insight.provider:qwen}")
    private String dashboardProvider;

    @Value("${ai.dashboard-insight.api-key:}")
    private String dashboardApiKey;

    @Value("${ai.dashboard-insight.base-url:}")
    private String dashboardBaseUrl;

    @Value("${ai.dashboard-insight.model:}")
    private String dashboardInsightModel;

    @Value("${ai.dashboard-insight.temperature:0.2}")
    private Double dashboardTemperature;

    @Value("${ai.dashboard-insight.max-tokens:1200}")
    private Integer dashboardMaxTokens;

    @Value("${ai.dashboard-insight.response-format:}")
    private String dashboardResponseFormat;

    @Value("${ai.dashboard-insight.reasoning-effort:}")
    private String dashboardReasoningEffort;

    @Value("${ai.dashboard-insight.image-mode:true}")
    private String imageMode;

    public DashboardInsightResponse analyze(
            DashboardInsightRequest request,
            Long userId,
            String authorizationHeader,
            AuthUserDetails user) {
        String systemPrompt = promptService.getSystemPrompt("dashboard_insight");
        String model = dashboardModel();
        boolean useImageInput = shouldUseImageInput(model);
        long start = System.currentTimeMillis();
        long screenshotMs = 0;
        long contextMs = 0;
        long modelMs = 0;
        long parseMs = 0;

        try {
            assertDashboardProviderConfigured();
            DashboardModelCallResult response;
            String textContext;
            if (useImageInput) {
                long screenshotStart = System.currentTimeMillis();
                byte[] imageBytes = getDashboardImageBytes(request, authorizationHeader, user);
                screenshotMs = System.currentTimeMillis() - screenshotStart;

                long contextStart = System.currentTimeMillis();
                textContext = buildContext(request, null, true);
                contextMs = System.currentTimeMillis() - contextStart;

                long modelStart = System.currentTimeMillis();
                response = callDashboardModel(systemPrompt, textContext, imageBytes, model);
                modelMs = System.currentTimeMillis() - modelStart;
            } else {
                long screenshotStart = System.currentTimeMillis();
                String visibleText = getDashboardVisibleText(request, authorizationHeader, user);
                screenshotMs = System.currentTimeMillis() - screenshotStart;

                long contextStart = System.currentTimeMillis();
                textContext = buildContext(request, visibleText, false);
                contextMs = System.currentTimeMillis() - contextStart;

                long modelStart = System.currentTimeMillis();
                response = callDashboardModel(systemPrompt, textContext, null, model);
                modelMs = System.currentTimeMillis() - modelStart;
            }

            String result = response.content();
            long latency = System.currentTimeMillis() - start;

            logService.log("dashboard_insight", model, dashboardProvider(),
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

    private String dashboardModel() {
        if (StringUtils.hasText(dashboardInsightModel)) {
            return dashboardInsightModel;
        }
        return StringUtils.hasText(visionModel) ? visionModel : chatModel;
    }

    private String dashboardProvider() {
        return StringUtils.hasText(dashboardProvider) ? dashboardProvider : "qwen";
    }

    private String dashboardApiKey() {
        return StringUtils.hasText(dashboardApiKey) ? dashboardApiKey : apiKey;
    }

    private String dashboardBaseUrl() {
        return StringUtils.hasText(dashboardBaseUrl) ? dashboardBaseUrl : qwenBaseUrl;
    }

    private void assertDashboardProviderConfigured() {
        String provider = dashboardProvider();
        if (!StringUtils.hasText(dashboardApiKey()) || "dummy_key_for_local_dev".equals(dashboardApiKey().trim())) {
            throw new IllegalStateException("AI_DASHBOARD_API_KEY 未配置或仍是本地占位值，请在 .env 中填入 "
                    + provider + " 的有效 API Key 后重启后端");
        }
        if (!StringUtils.hasText(dashboardBaseUrl())) {
            throw new IllegalStateException("AI_DASHBOARD_BASE_URL 未配置，请在 .env 中填入 "
                    + provider + " 的 OpenAI-compatible Base URL 后重启后端");
        }
    }

    private boolean shouldUseImageInput(String model) {
        if ("true".equalsIgnoreCase(imageMode)) return true;
        if ("false".equalsIgnoreCase(imageMode)) return false;
        String normalized = model != null ? model.toLowerCase() : "";
        return normalized.contains("vl")
                || normalized.contains("vision")
                || normalized.contains("omni");
    }

    private DashboardModelCallResult callDashboardModel(
            String systemPrompt,
            String textContext,
            byte[] imageBytes,
            String model) {
        String endpoint = chatCompletionsEndpoint(dashboardBaseUrl());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages(systemPrompt, textContext, imageBytes));
        body.put("temperature", dashboardTemperature != null ? dashboardTemperature : 0.2);
        body.put("max_tokens", dashboardMaxTokens != null ? dashboardMaxTokens : 1200);
        if (StringUtils.hasText(dashboardResponseFormat)) {
            body.put("response_format", Map.of("type", dashboardResponseFormat));
        }
        if (StringUtils.hasText(dashboardReasoningEffort)) {
            body.put("reasoning_effort", dashboardReasoningEffort);
        }

        try {
            Map<String, Object> response = restClientBuilder.build()
                    .post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + dashboardApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(MAP_RESPONSE_TYPE);
            return parseModelResponse(response);
        } catch (RestClientResponseException e) {
            throw new IllegalStateException("AI_DASHBOARD 调用失败: HTTP " + e.getStatusCode().value()
                    + " " + truncate(e.getResponseBodyAsString(), 500), e);
        }
    }

    private List<Map<String, Object>> messages(String systemPrompt, String textContext, byte[] imageBytes) {
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("content", systemPrompt);

        Map<String, Object> user = new LinkedHashMap<>();
        user.put("role", "user");
        if (imageBytes == null) {
            user.put("content", textContext);
        } else {
            Map<String, Object> textPart = new LinkedHashMap<>();
            textPart.put("type", "text");
            textPart.put("text", textContext);

            Map<String, Object> imageUrl = new LinkedHashMap<>();
            imageUrl.put("url", "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes));

            Map<String, Object> imagePart = new LinkedHashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", imageUrl);

            user.put("content", List.of(textPart, imagePart));
        }
        return List.of(system, user);
    }

    private String chatCompletionsEndpoint(String baseUrl) {
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/chat/completions")) {
            return normalized;
        }
        if (normalized.endsWith("/v1")) {
            return normalized + "/chat/completions";
        }
        return normalized + "/v1/chat/completions";
    }

    private DashboardModelCallResult parseModelResponse(Map<String, Object> response) {
        if (response == null) {
            throw new IllegalStateException("AI_DASHBOARD 返回为空");
        }
        Object choicesValue = response.get("choices");
        if (!(choicesValue instanceof List<?> choices) || choices.isEmpty()) {
            throw new IllegalStateException("AI_DASHBOARD 返回缺少 choices: " + truncate(response.toString(), 500));
        }
        Object firstChoice = choices.get(0);
        if (!(firstChoice instanceof Map<?, ?> choice)) {
            throw new IllegalStateException("AI_DASHBOARD choices 格式异常: " + truncate(response.toString(), 500));
        }
        Object messageValue = choice.get("message");
        if (!(messageValue instanceof Map<?, ?> message)) {
            throw new IllegalStateException("AI_DASHBOARD 返回缺少 message: " + truncate(response.toString(), 500));
        }

        String content = extractContent(message.get("content"));
        long totalTokens = totalTokens(response.get("usage"));
        return new DashboardModelCallResult(content, totalTokens);
    }

    private String extractContent(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof List<?> parts) {
            StringBuilder sb = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof Map<?, ?> partMap) {
                    Object text = partMap.get("text");
                    if (text != null) {
                        sb.append(text);
                    }
                } else if (part != null) {
                    sb.append(part);
                }
            }
            return sb.toString();
        }
        return content.toString();
    }

    private long totalTokens(Object usage) {
        if (!(usage instanceof Map<?, ?> usageMap)) {
            return 0;
        }
        Object totalTokens = usageMap.get("total_tokens");
        if (totalTokens instanceof Number number) {
            return number.longValue();
        }
        return 0;
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

            return DashboardInsightResponse.builder()
                    .summary(stringField(parsed, "summary"))
                    .severity(stringField(parsed, "severity"))
                    .findings(findingListField(parsed, "findings"))
                    .recommendations(stringListField(parsed, "recommendations"))
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

    private record DashboardModelCallResult(String content, long totalTokens) {
    }
}
