package com.iov.platform.modules.ai.controller;

import com.iov.platform.common.Result;
import com.iov.platform.modules.ai.dto.PingRequest;
import com.iov.platform.modules.ai.dto.PingResponse;
import com.iov.platform.modules.ai.service.AiCallLogService;
import com.iov.platform.modules.ai.service.AiChatGateway;
import com.iov.platform.modules.ai.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiPingController {

    private final AiChatGateway chatGateway;
    private final PromptTemplateService promptService;
    private final AiCallLogService logService;

    @PostMapping("/ping")
    public Result<PingResponse> ping(@RequestBody(required = false) PingRequest request) {
        String userMessage = request != null && request.getMessage() != null
                ? request.getMessage()
                : "Hello, 请用中文回复'AI接入成功'并简单介绍你自己。";

        String model = request != null && StringUtils.hasText(request.getModel())
                ? request.getModel()
                : chatGateway.getDefaultModel();

        String provider = chatGateway.getProvider();
        String systemPrompt = promptService.getSystemPrompt("ping");

        try {
            AiChatGateway.ChatResult result = chatGateway.chat(new AiChatGateway.ChatRequest(
                    model,
                    systemPrompt,
                    userMessage,
                    null,
                    false
            ));

            logService.log("ping", model, provider,
                    "system=" + systemPrompt + "; user=" + userMessage,
                    result.content(), true, (int) result.latencyMs(), (int) result.totalTokens(), null);

            return Result.ok(PingResponse.builder()
                    .reply(result.content())
                    .model(result.model())
                    .provider(result.provider())
                    .latencyMs(result.latencyMs())
                    .build());
        } catch (Exception e) {
            logService.log("ping", model, provider,
                    "system=" + systemPrompt + "; user=" + userMessage,
                    "ERROR: " + e.getMessage(),
                    false, null, null, null);
            log.error("AI ping failed", e);
            return Result.fail(500, "AI 调用失败: " + e.getMessage());
        }
    }
}
