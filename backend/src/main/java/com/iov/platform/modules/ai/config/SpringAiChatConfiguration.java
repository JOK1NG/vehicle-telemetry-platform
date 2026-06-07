package com.iov.platform.modules.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(AiChatProperties.class)
public class SpringAiChatConfiguration {

    private static final String DEFAULT_COMPLETIONS_PATH = "/v1/chat/completions";

    @Bean
    public OpenAiChatModel openAiChatModel(
            AiChatProperties properties,
            RestClient.Builder restClientBuilder,
            WebClient.Builder webClientBuilder) {
        Endpoint endpoint = normalizeEndpoint(properties.getBaseUrl(), properties.getCompletionsPath());
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(endpoint.baseUrl())
                .completionsPath(endpoint.completionsPath())
                .apiKey(effectiveApiKey(properties.getApiKey()))
                .restClientBuilder(restClientBuilder)
                .webClientBuilder(webClientBuilder)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(defaultOptions(properties))
                .retryTemplate(RetryTemplate.defaultInstance())
                .build();
    }

    @Bean
    public ChatClient.Builder aiChatClientBuilder(@NonNull OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel);
    }

    private static OpenAiChatOptions defaultOptions(AiChatProperties properties) {
        return OpenAiChatOptions.builder()
                .model(properties.getModel())
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .streamUsage(Boolean.TRUE.equals(properties.getStreamUsage()))
                .build();
    }

    private static String effectiveApiKey(String apiKey) {
        return StringUtils.hasText(apiKey) ? apiKey : "dummy_key_for_local_dev";
    }

    private static Endpoint normalizeEndpoint(String rawBaseUrl, String rawCompletionsPath) {
        String baseUrl = StringUtils.hasText(rawBaseUrl)
                ? rawBaseUrl.trim()
                : "https://api.stepfun.com/step_plan";
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String completionsPath = StringUtils.hasText(rawCompletionsPath)
                ? ensureLeadingSlash(rawCompletionsPath.trim())
                : DEFAULT_COMPLETIONS_PATH;

        if (baseUrl.endsWith("/chat/completions")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/chat/completions".length());
            completionsPath = "/chat/completions";
        }
        if (baseUrl.endsWith("/v1")
                && (DEFAULT_COMPLETIONS_PATH.equals(completionsPath) || "/chat/completions".equals(completionsPath))) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - "/v1".length());
            completionsPath = DEFAULT_COMPLETIONS_PATH;
        }
        return new Endpoint(baseUrl, completionsPath);
    }

    private static String ensureLeadingSlash(String value) {
        return value.startsWith("/") ? value : "/" + value;
    }

    private record Endpoint(String baseUrl, String completionsPath) {}
}
