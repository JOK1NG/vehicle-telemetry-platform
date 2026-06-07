package com.iov.platform.modules.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.chat")
public class AiChatProperties {

    private String provider = "stepfun";
    private String apiKey = "dummy_key_for_local_dev";
    private String baseUrl = "https://api.stepfun.com/step_plan";
    private String completionsPath = "/v1/chat/completions";
    private String model = "step-3.7-flash";
    private Double temperature = 0.2;
    private Integer maxTokens = 2400;
    private String responseFormat = "json_object";
    private String reasoningEffort = "";
    private Boolean streamUsage = false;
}
