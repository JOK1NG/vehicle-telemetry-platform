package com.iov.platform.modules.ai.controller;

import com.iov.platform.common.Result;
import com.iov.platform.modules.ai.dto.DashboardInsightRequest;
import com.iov.platform.modules.ai.dto.DashboardInsightResponse;
import com.iov.platform.modules.ai.dto.TelemetryInsightRequest;
import com.iov.platform.modules.ai.dto.TelemetryInsightResponse;
import com.iov.platform.modules.ai.dto.TelemetryInsightStreamEvent;
import com.iov.platform.modules.ai.service.DashboardInsightService;
import com.iov.platform.modules.ai.service.TelemetryInsightService;
import com.iov.platform.modules.auth.service.AuthUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@RestController
@RequestMapping("/api/ai/insights")
@RequiredArgsConstructor
public class AiInsightController {
    private static final long TELEMETRY_STREAM_TIMEOUT_MS = 120_000L;

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

    @PostMapping(value = "/telemetry/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTelemetry(
            @Valid @RequestBody TelemetryInsightRequest request,
            @AuthenticationPrincipal AuthUserDetails user) {
        Long userId = user != null ? user.getSysUser().getId() : null;
        SseEmitter emitter = new SseEmitter(TELEMETRY_STREAM_TIMEOUT_MS);
        AtomicReference<Disposable> subscriptionRef = new AtomicReference<>();
        Disposable subscription = telemetryInsightService.streamAnalyze(request, userId)
                .subscribe(
                        event -> {
                            try {
                                sendTelemetryEvent(emitter, event);
                            } catch (RuntimeException e) {
                                dispose(subscriptionRef.get());
                                completeSilently(emitter);
                            }
                        },
                        error -> {
                            log.warn("Telemetry insight SSE failed: {}", error.getMessage());
                            try {
                                sendErrorAndComplete(emitter,
                                        "AI 流式诊断失败：" + error.getMessage(),
                                        0);
                            } catch (RuntimeException sendError) {
                                completeSilently(emitter);
                            } finally {
                                dispose(subscriptionRef.get());
                            }
                        },
                        () -> {
                            try {
                                emitter.complete();
                            } finally {
                                dispose(subscriptionRef.get());
                            }
                        }
                );
        subscriptionRef.set(subscription);
        emitter.onCompletion(() -> dispose(subscriptionRef.get()));
        emitter.onTimeout(() -> {
            dispose(subscriptionRef.get());
            try {
                sendErrorAndComplete(emitter,
                        "AI 流式诊断超时（120 秒内未返回最终结果），请稍后重试。",
                        TELEMETRY_STREAM_TIMEOUT_MS);
            } catch (RuntimeException e) {
                completeSilently(emitter);
            }
        });
        emitter.onError(error -> {
            dispose(subscriptionRef.get());
            log.debug("Telemetry insight SSE connection closed with error: {}", error.getMessage());
            completeSilently(emitter);
        });
        return emitter;
    }

    private void sendTelemetryEvent(SseEmitter emitter, TelemetryInsightStreamEvent event) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event.getType())
                    .data(event));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write telemetry insight SSE event", e);
        }
    }

    private void dispose(Disposable subscription) {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }

    private void sendErrorAndComplete(SseEmitter emitter, String message, long elapsedMs) {
        sendTelemetryEvent(emitter, TelemetryInsightStreamEvent.error(message, elapsedMs));
        completeSilently(emitter);
    }

    private void completeSilently(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("Failed to complete SSE emitter: {}", e.getMessage());
        }
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
