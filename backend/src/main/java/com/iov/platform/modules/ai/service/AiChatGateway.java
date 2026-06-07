package com.iov.platform.modules.ai.service;

import reactor.core.publisher.Flux;

public interface AiChatGateway {

    ChatResult chat(ChatRequest request);

    Flux<String> stream(ChatRequest request);

    String getProvider();

    String getDefaultModel();

    record ChatRequest(
            String model,
            String systemPrompt,
            String userMessage,
            byte[] imagePng,
            boolean jsonMode) {
    }

    record ChatResult(
            String content,
            long totalTokens,
            long latencyMs,
            String model,
            String provider) {
    }
}
