package com.iov.platform.modules.ai.controller;

import com.iov.platform.common.Result;
import com.iov.platform.modules.ai.dto.DashboardInsightRequest;
import com.iov.platform.modules.ai.dto.DashboardInsightResponse;
import com.iov.platform.modules.ai.dto.TelemetryInsightRequest;
import com.iov.platform.modules.ai.dto.TelemetryInsightResponse;
import com.iov.platform.modules.ai.service.DashboardInsightService;
import com.iov.platform.modules.ai.service.TelemetryInsightService;
import com.iov.platform.modules.auth.service.AuthUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/ai/insights")
@RequiredArgsConstructor
public class AiInsightController {

    private final TelemetryInsightService telemetryInsightService;
    private final DashboardInsightService dashboardInsightService;

    @PostMapping("/telemetry")
    public Result<TelemetryInsightResponse> analyzeTelemetry(
            @Valid @RequestBody TelemetryInsightRequest request,
            @AuthenticationPrincipal AuthUserDetails user) {
        Long userId = user != null ? user.getSysUser().getId() : null;
        TelemetryInsightResponse response = telemetryInsightService.analyze(request, userId);
        return Result.ok(response);
    }

    @PostMapping("/dashboard")
    public Result<DashboardInsightResponse> analyzeDashboard(
            @Valid @RequestBody DashboardInsightRequest request,
            @AuthenticationPrincipal AuthUserDetails user,
            HttpServletRequest httpRequest) {
        Long userId = user != null ? user.getSysUser().getId() : null;
        DashboardInsightResponse response = dashboardInsightService.analyze(
                request,
                userId,
                httpRequest.getHeader("Authorization"),
                user
        );
        if ("UNKNOWN".equals(response.getSeverity()) && response.getSummary().startsWith("分析失败")) {
            return Result.fail(500, response.getSummary());
        }
        return Result.ok(response);
    }
}
