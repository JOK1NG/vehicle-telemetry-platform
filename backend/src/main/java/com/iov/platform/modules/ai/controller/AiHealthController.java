package com.iov.platform.modules.ai.controller;

import com.iov.platform.common.Result;
import com.iov.platform.modules.ai.config.AiChatProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiHealthController {

    private final AiChatProperties properties;

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        String apiKey = properties.getApiKey();
        String baseUrl = properties.getBaseUrl();
        boolean configured = StringUtils.hasText(apiKey)
                && !"dummy_key_for_local_dev".equals(apiKey.trim())
                && StringUtils.hasText(baseUrl);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", configured ? "UP" : "DOWN");
        data.put("provider", properties.getProvider());
        data.put("model", properties.getModel());
        data.put("baseUrl", baseUrl);
        return Result.ok(data);
    }
}
