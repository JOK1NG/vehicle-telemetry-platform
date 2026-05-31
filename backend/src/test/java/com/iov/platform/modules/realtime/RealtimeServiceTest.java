package com.iov.platform.modules.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.modules.realtime.dto.TelemetryMessage;
import com.iov.platform.modules.realtime.dto.VehicleUpdateMessage;
import com.iov.platform.modules.realtime.service.RealtimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.*;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.GenericMessage;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RealtimeService 单元测试 (修复 #14)
 */
class RealtimeServiceTest {

    private StringRedisTemplate redis;
    private JdbcTemplate jdbc;
    private SimpMessagingTemplate messagingTemplate;
    private ObjectMapper objectMapper;
    private RealtimeService service;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        jdbc = mock(JdbcTemplate.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        objectMapper = new ObjectMapper();

        // Mock Redis operations
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        ValueOperations<String, String> valOps = mock(ValueOperations.class);

        when(redis.opsForHash()).thenReturn(hashOps);
        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForValue()).thenReturn(valOps);

        service = new RealtimeService(redis, jdbc, messagingTemplate, objectMapper);
    }

    // ===== extractVehicleId =====

    @Test
    void extractVehicleId_validTopic() throws Exception {
        Method m = RealtimeService.class.getDeclaredMethod("extractVehicleId", String.class);
        m.setAccessible(true);
        assertEquals(42L, m.invoke(service, "vehicle/42/telemetry"));
    }

    @Test
    void extractVehicleId_invalidTopic() throws Exception {
        Method m = RealtimeService.class.getDeclaredMethod("extractVehicleId", String.class);
        m.setAccessible(true);
        assertNull(m.invoke(service, "bad/topic"));
        assertNull(m.invoke(service, "vehicle//telemetry"));
        assertNull(m.invoke(service, "vehicle/abc/telemetry"));
        assertNull(m.invoke(service, ""));
        assertNull(m.invoke(service, (String) null));
    }

    // ===== parseTs =====

    @Test
    void parseTs_validIso() throws Exception {
        Method m = RealtimeService.class.getDeclaredMethod("parseTs", String.class);
        m.setAccessible(true);
        assertNotNull(m.invoke(service, "2026-06-01T08:30:00.000Z"));
    }

    @Test
    void parseTs_invalidReturnsNull() throws Exception {
        Method m = RealtimeService.class.getDeclaredMethod("parseTs", String.class);
        m.setAccessible(true);
        assertNull(m.invoke(service, "bad"));
        assertNull(m.invoke(service, (String) null));
        assertNull(m.invoke(service, ""));
    }

    // ===== handleTelemetry =====

    @Test
    void handleTelemetry_validMessage() throws Exception {
        String payload = "{\"ts\":\"2026-06-01T08:30:00.000Z\",\"lng\":121.473701,\"lat\":31.230416,\"speed\":42.5,\"heading\":90.0,\"battery\":78.3,\"faultCode\":null}";

        Message<String> msg = new GenericMessage<>(payload,
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/1/telemetry"));

        service.handleTelemetry(msg);

        // 验证 Redis 写入
        verify(redis.opsForHash()).putAll(eq("vehicle:rt:1"), anyMap());
        verify(redis).expire(eq("vehicle:rt:1"), any());
        verify(redis.opsForSet()).add("vehicle:online", "1");
        // 验证 DB 写入 (含 ST_MakePoint)
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleTelemetry_invalidLng_rejected() {
        String payload = "{\"ts\":\"2026-06-01T08:30:00.000Z\",\"lng\":999.0,\"lat\":31.0,\"speed\":42.5,\"heading\":90.0,\"battery\":78.3}";

        Message<String> msg = new GenericMessage<>(payload,
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/1/telemetry"));

        service.handleTelemetry(msg);

        // 不应写入 DB（校验拒绝）
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleTelemetry_invalidJson_skipped() {
        Message<String> msg = new GenericMessage<>("not json",
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/1/telemetry"));

        service.handleTelemetry(msg);
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ===== cleanStaleOnlineSet =====

    @Test
    void cleanStaleOnlineSet_removesExpired() {
        when(redis.opsForSet().members("vehicle:online")).thenReturn(
                java.util.Set.of("1", "2"));
        when(redis.hasKey("vehicle:rt:1")).thenReturn(true);
        when(redis.hasKey("vehicle:rt:2")).thenReturn(false);  // 过期

        service.cleanStaleOnlineSet();

        verify(redis.opsForSet()).remove("vehicle:online", "2");
        verify(redis.opsForSet(), never()).remove("vehicle:online", "1");
    }
}
