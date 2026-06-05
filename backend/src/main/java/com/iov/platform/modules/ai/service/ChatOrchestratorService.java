package com.iov.platform.modules.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatOrchestratorService {

    private final ChatClient.Builder chatClientBuilder;
    private final AiCallLogService logService;

    public String chat(String scene, String model, String provider,
                       String systemPrompt, String userMessage,
                       Long userId) {
        long start = System.currentTimeMillis();
        try {
            var spec = chatClientBuilder.build().prompt();

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                spec = spec.system(systemPrompt);
            }

            spec = spec.user(userMessage);

            if (model != null && !model.isBlank()) {
                spec = spec.options(OpenAiChatOptions.builder().model(model).build());
            }

            ChatResponse response = spec.call().chatResponse();

            String result = response.getResult().getOutput().getText();
            long latency = System.currentTimeMillis() - start;

            var metadata = response.getMetadata();
            Usage usage = metadata != null ? metadata.getUsage() : null;
            long tokens = usage != null ? usage.getTotalTokens() : 0;

            logService.log(scene, modelOrDefault(model), provider,
                    reqSummary(systemPrompt, userMessage),
                    truncate(result, 500), true, (int) latency, (int) tokens, userId);
            return result;
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            logService.log(scene, modelOrDefault(model), provider,
                    reqSummary(systemPrompt, userMessage),
                    "ERROR: " + truncate(e.getMessage(), 500),
                    false, (int) latency, null, userId);
            throw new RuntimeException("AI call failed: " + e.getMessage(), e);
        }
    }

    private static String modelOrDefault(String model) {
        return model != null && !model.isBlank() ? model : "default";
    }

    private static String reqSummary(String system, String user) {
        return "system=" + truncate(system, 500) + "; user=" + truncate(user, 500);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
