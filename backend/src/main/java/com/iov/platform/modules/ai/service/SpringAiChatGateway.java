package com.iov.platform.modules.ai.service;

import com.iov.platform.modules.ai.config.AiChatProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpringAiChatGateway implements AiChatGateway {

    private final ChatClient.Builder chatClientBuilder;
    private final AiChatProperties properties;

    @Override
    public ChatResult chat(ChatRequest request) {
        assertConfigured();
        long start = System.currentTimeMillis();
        String model = effectiveModel(request.model());
        ChatResponse response = requestSpec(request, model).call().chatResponse();
        long latency = System.currentTimeMillis() - start;
        String content = response.getResult().getOutput().getText();
        Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
        long tokens = usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : 0;
        return new ChatResult(content, tokens, latency, model, getProvider());
    }

    @Override
    public Flux<String> stream(ChatRequest request) {
        assertConfigured();
        String model = effectiveModel(request.model());
        return requestSpec(request, model).stream().content();
    }

    @Override
    public String getProvider() {
        return StringUtils.hasText(properties.getProvider()) ? properties.getProvider() : "openai-compatible";
    }

    @Override
    public String getDefaultModel() {
        return effectiveModel(null);
    }

    private ChatClientRequestSpec requestSpec(ChatRequest request, String model) {
        ChatClientRequestSpec spec = chatClientBuilder.build().prompt();
        if (StringUtils.hasText(request.systemPrompt())) {
            spec = spec.system(request.systemPrompt());
        }
        if (request.imagePng() == null || request.imagePng().length == 0) {
            spec = spec.user(request.userMessage());
        } else {
            ByteArrayResource image = new ByteArrayResource(request.imagePng()) {
                @Override
                public String getFilename() {
                    return "dashboard.png";
                }
            };
            spec = spec.user(user -> user
                    .text(request.userMessage())
                    .media(MimeTypeUtils.IMAGE_PNG, image));
        }
        return spec.options(options(model, request.jsonMode()));
    }

    private OpenAiChatOptions options(String model, boolean jsonMode) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(model)
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .streamUsage(Boolean.TRUE.equals(properties.getStreamUsage()));

        if (jsonMode && StringUtils.hasText(properties.getResponseFormat())) {
            builder.responseFormat(ResponseFormat.builder()
                    .type(responseFormatType(properties.getResponseFormat()))
                    .build());
        }
        if (StringUtils.hasText(properties.getReasoningEffort())) {
            builder.reasoningEffort(properties.getReasoningEffort());
        }
        return builder.build();
    }

    private ResponseFormat.Type responseFormatType(String raw) {
        String normalized = raw.trim().replace('-', '_').toUpperCase();
        try {
            return ResponseFormat.Type.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid AI response format '{}', defaulting to JSON_OBJECT", raw);
            return ResponseFormat.Type.JSON_OBJECT;
        }
    }

    private String effectiveModel(String model) {
        return StringUtils.hasText(model) ? model : properties.getModel();
    }

    private void assertConfigured() {
        String apiKey = properties.getApiKey();
        if (!StringUtils.hasText(apiKey) || "dummy_key_for_local_dev".equals(apiKey.trim())) {
            throw new IllegalStateException("AI_CHAT_API_KEY/AI_DASHBOARD_API_KEY 未配置或仍是本地占位值，请填入 "
                    + getProvider() + " 的有效 API Key 后重启后端");
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            throw new IllegalStateException("AI_CHAT_BASE_URL/AI_DASHBOARD_BASE_URL 未配置");
        }
    }
}
