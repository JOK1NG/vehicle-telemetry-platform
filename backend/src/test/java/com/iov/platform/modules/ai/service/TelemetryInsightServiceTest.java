package com.iov.platform.modules.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.modules.ai.dto.TelemetryInsightRequest;
import com.iov.platform.modules.ai.dto.TelemetryInsightResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelemetryInsightServiceTest {

    private JdbcTemplate jdbcTemplate;
    private AiChatGateway chatGateway;
    private AiCallLogService logService;
    private TelemetryInsightService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        chatGateway = mock(AiChatGateway.class);
        logService = mock(AiCallLogService.class);

        when(chatGateway.getDefaultModel()).thenReturn("step-3.7-flash");
        when(chatGateway.getProvider()).thenReturn("stepfun");

        service = new TelemetryInsightService(
                jdbcTemplate,
                chatGateway,
                new PromptTemplateService(),
                new ObjectMapper(),
                logService
        );
    }

    @Test
    void analyze_validJson_returnsWithoutRetry() {
        when(chatGateway.chat(any())).thenReturn(result("""
                {
                  "summary": "电池轻微下降，整体运行稳定。",
                  "severity": "LOW",
                  "findings": ["速度波动正常", "电池下降 0.1%"],
                  "recommendations": ["继续观察电池趋势"]
                }
                """, 19, 820));

        TelemetryInsightResponse response = service.analyze(requestWithMetrics(), 101L);

        assertEquals("LOW", response.getSeverity());
        assertEquals("电池轻微下降，整体运行稳定。", response.getSummary());
        assertEquals(List.of("速度波动正常", "电池下降 0.1%"), response.getFindings());
        assertEquals(820, response.getLatencyMs());
        verify(chatGateway, times(1)).chat(any());
        verifyNoInteractions(jdbcTemplate);
        verify(logService).log(
                eq("telemetry_insight"),
                eq("step-3.7-flash"),
                eq("stepfun"),
                anyString(),
                anyString(),
                eq(true),
                eq(820),
                eq(19),
                eq(101L)
        );
    }

    @Test
    void analyze_missingSummary_usesFindingFallbackWithoutRetry() {
        when(chatGateway.chat(any())).thenReturn(result("""
                {
                  "severity": "MEDIUM",
                  "findings": ["电池下降速度偏快"],
                  "recommendations": ["复核近 15 分钟电池曲线"]
                }
                """, 23, 940));

        TelemetryInsightResponse response = service.analyze(requestWithMetrics(), 102L);

        assertEquals("MEDIUM", response.getSeverity());
        assertEquals("发现 1 项遥测现象：电池下降速度偏快", response.getSummary());
        verify(chatGateway, times(1)).chat(any());
        verify(logService).log(
                eq("telemetry_insight"),
                eq("step-3.7-flash"),
                eq("stepfun"),
                anyString(),
                anyString(),
                eq(true),
                eq(940),
                eq(23),
                eq(102L)
        );
    }

    @Test
    void analyze_echoedInput_retriesWithStrongerPromptAndReturnsSecondResponse() {
        when(chatGateway.chat(any()))
                .thenReturn(result("{\"vehicleId\":1}", 8, 120))
                .thenReturn(result("""
                        {
                          "summary": "车辆存在中等风险，需要复核电池与航向。",
                          "severity": "MEDIUM",
                          "findings": ["电池下降快", "航向波动偏大"],
                          "recommendations": ["检查电池包", "查看轨迹是否异常"]
                        }
                        """, 42, 1380));

        TelemetryInsightResponse response = service.analyze(requestWithMetrics(), 103L);

        assertEquals("MEDIUM", response.getSeverity());
        assertEquals("车辆存在中等风险，需要复核电池与航向。", response.getSummary());

        ArgumentCaptor<AiChatGateway.ChatRequest> captor =
                ArgumentCaptor.forClass(AiChatGateway.ChatRequest.class);
        verify(chatGateway, times(2)).chat(captor.capture());
        assertTrue(captor.getAllValues().get(0).jsonMode());
        assertTrue(captor.getAllValues().get(1).jsonMode());
        assertTrue(captor.getAllValues().get(1).userMessage().contains("上一次输出未满足接口 JSON schema"));
        assertTrue(captor.getAllValues().get(1).userMessage().contains("禁止复述输入"));

        verify(logService).log(
                eq("telemetry_insight"),
                eq("step-3.7-flash"),
                eq("stepfun"),
                anyString(),
                eq("{\"vehicleId\":1}"),
                eq(false),
                eq(120),
                eq(8),
                eq(103L)
        );
        verify(logService).log(
                eq("telemetry_insight"),
                eq("step-3.7-flash"),
                eq("stepfun"),
                anyString(),
                anyString(),
                eq(true),
                eq(1380),
                eq(42),
                eq(103L)
        );
    }

    @Test
    void analyze_invalidSeverity_retriesAndNormalizesSecondSeverity() {
        when(chatGateway.chat(any()))
                .thenReturn(result("""
                        {
                          "summary": "车辆可能存在异常。",
                          "severity": "WARN",
                          "findings": ["速度波动较大"],
                          "recommendations": ["继续观察"]
                        }
                        """, 12, 300))
                .thenReturn(result("""
                        {
                          "summary": "车辆风险较低。",
                          "severity": "low",
                          "findings": ["速度波动可接受"],
                          "recommendations": ["保持监控"]
                        }
                        """, 18, 610));

        TelemetryInsightResponse response = service.analyze(requestWithMetrics(), 104L);

        assertEquals("LOW", response.getSeverity());
        assertEquals("车辆风险较低。", response.getSummary());
        verify(chatGateway, times(2)).chat(any());
    }

    @Test
    void analyze_gatewayException_retriesAndLogsFailedAttempt() {
        when(chatGateway.chat(any()))
                .thenThrow(new RuntimeException("provider timeout"))
                .thenReturn(result("""
                        {
                          "summary": "车辆暂无明显异常。",
                          "severity": "LOW",
                          "findings": ["指标处于正常范围"],
                          "recommendations": ["按计划巡检"]
                        }
                        """, 16, 720));

        TelemetryInsightResponse response = service.analyze(requestWithMetrics(), 105L);

        assertEquals("LOW", response.getSeverity());
        verify(chatGateway, times(2)).chat(any());
        verify(logService).log(
                eq("telemetry_insight"),
                eq("step-3.7-flash"),
                eq("stepfun"),
                anyString(),
                eq("ERROR: provider timeout"),
                eq(false),
                isNull(),
                isNull(),
                eq(105L)
        );
        verify(logService).log(
                eq("telemetry_insight"),
                eq("step-3.7-flash"),
                eq("stepfun"),
                anyString(),
                anyString(),
                eq(true),
                eq(720),
                eq(16),
                eq(105L)
        );
    }

    private static AiChatGateway.ChatResult result(String content, long totalTokens, long latencyMs) {
        return new AiChatGateway.ChatResult(content, totalTokens, latencyMs, "step-3.7-flash", "stepfun");
    }

    private static TelemetryInsightRequest requestWithMetrics() {
        TelemetryInsightRequest request = new TelemetryInsightRequest();
        request.setVehicleId(1L);
        TelemetryInsightRequest.TimeRange timeRange = new TelemetryInsightRequest.TimeRange();
        timeRange.setStart("2026-06-06T10:00:00Z");
        timeRange.setEnd("2026-06-06T10:15:00Z");
        request.setTimeRange(timeRange);
        request.setMetrics(Map.of(
                "speed", List.of(40.0, 42.0, 38.0),
                "heading", List.of(90.0, 92.0, 95.0),
                "battery", List.of(80.0, 79.8, 79.5)
        ));
        request.setAlerts(List.of("battery_drop_watch"));
        return request;
    }
}
