package com.iov.platform.config;

import com.iov.platform.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基础健康检查接口 (M0)
 * 同时保留 /actuator/health (来自 spring-boot-starter-actuator)
 */
@RestController
public class HealthController {

    @GetMapping({"/health", "/"})
    public Result<String> health() {
        return Result.ok("vehicle-telemetry-backend is running");
    }
}
