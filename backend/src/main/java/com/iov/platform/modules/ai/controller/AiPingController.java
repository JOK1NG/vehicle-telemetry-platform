package com.iov.platform.modules.ai.controller;

import com.iov.platform.common.Result;
import com.iov.platform.modules.ai.dto.PingRequest;
import com.iov.platform.modules.ai.dto.PingResponse;
import com.iov.platform.modules.ai.service.ChatOrchestratorService;
import com.iov.platform.modules.ai.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiPingController {

    private final ChatOrchestratorService orchestrator;
    private final PromptTemplateService promptService;

    @Value("${spring.ai.openai.chat.options.model:qwen3.7-plus}")
    private String defaultModel;

    @PostMapping("/ping")
    public Result<PingResponse> ping(@RequestBody(required = false) PingRequest request) {
        String userMessage = request != null && request.getMessage() != null
                ? request.getMessage()
                : "Hello, 请用中文回复'AI接入成功'并简单介绍你自己。";

        String model = request != null && request.getModel() != null
                ? request.getModel()
                : defaultModel;

        long start = System.currentTimeMillis();
        try {
            String reply = orchestrator.chat(
                    "ping",
                    model,
                    "qwen",
                    promptService.getSystemPrompt("ping"),
                    userMessage,
                    null
            );
            long latency = System.currentTimeMillis() - start;
            return Result.ok(PingResponse.builder()
                    .reply(reply)
                    .model(model)
                    .provider("qwen")
                    .latencyMs(latency)
                    .build());
        } catch (Exception e) {
            log.error("AI ping failed", e);
            return Result.fail(500, "AI 调用失败: " + e.getMessage());
        }
    }
}
