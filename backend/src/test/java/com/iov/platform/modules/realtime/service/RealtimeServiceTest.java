package com.iov.platform.modules.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.modules.realtime.dto.TelemetryMessage;
import com.iov.platform.modules.realtime.dto.VehicleUpdateMessage;
import com.iov.platform.modules.realtime.service.RealtimeService;
import com.iov.platform.modules.vehicle.entity.Vehicle;
import com.iov.platform.modules.vehicle.mapper.VehicleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.*;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.GenericMessage;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * RealtimeService 单元测试 (含 MUL-31 plateNo 逻辑)
 */
class RealtimeServiceTest {

    private StringRedisTemplate redis;
    private JdbcTemplate jdbc;
    private SimpMessagingTemplate messagingTemplate;
    private ObjectMapper objectMapper;
    private VehicleMapper vehicleMapper;
    private RealtimeService service;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        jdbc = mock(JdbcTemplate.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        objectMapper = new ObjectMapper();
        vehicleMapper = mock(VehicleMapper.class);

        // Mock Redis operations
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        ValueOperations<String, String> valOps = mock(ValueOperations.class);

        when(redis.opsForHash()).thenReturn(hashOps);
        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForValue()).thenReturn(valOps);

        service = new RealtimeService(redis, jdbc, messagingTemplate, objectMapper, vehicleMapper);
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

    // ===== handleTelemetry + plateNo =====

    @Test
    void handleTelemetry_validMessage_plateNoInBufferAndBroadcast() throws Exception {
        String payload = "{\"ts\":\"2026-06-01T08:30:00.000Z\",\"lng\":121.473701,\"lat\":31.230416,\"speed\":42.5,\"heading\":90.0,\"battery\":78.3,\"faultCode\":null}";

        Message<String> msg = new GenericMessage<>(payload,
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/1/telemetry"));

        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNo("沪A12345");
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);

        service.handleTelemetry(msg);

        // 验证 Redis 写入
        verify(redis.opsForHash()).putAll(eq("vehicle:rt:1"), anyMap());
        verify(redis).expire(eq("vehicle:rt:1"), any());
        verify(redis.opsForSet()).add("vehicle:online", "1");
        // 验证 DB 写入 (含 ST_MakePoint)
        verify(jdbc).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

        // 验证 plateNo 进入 WebSocket 广播
        service.flushBuffer();
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/vehicles"), captor.capture());
        Map<String, Object> envelope = captor.getValue();
        @SuppressWarnings("unchecked")
        List<VehicleUpdateMessage> vehicles = (List<VehicleUpdateMessage>) envelope.get("vehicles");
        assertNotNull(vehicles);
        assertEquals(1, vehicles.size());
        assertEquals("沪A12345", vehicles.get(0).getPlateNo());
    }

    @Test
    void handleTelemetry_bufferReuse_avoidsDuplicateDbQuery() {
        String payload = "{\"ts\":\"2026-06-01T08:30:00.000Z\",\"lng\":121.473701,\"lat\":31.230416,\"speed\":42.5,\"heading\":90.0,\"battery\":78.3}";

        Message<String> msg1 = new GenericMessage<>(payload,
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/1/telemetry"));
        Message<String> msg2 = new GenericMessage<>(payload,
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/1/telemetry"));

        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNo("沪A12345");
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);

        service.handleTelemetry(msg1);
        service.handleTelemetry(msg2);

        // 同一车辆在 buffer 被 flush 前只应查一次 DB
        verify(vehicleMapper, times(1)).selectById(1L);

        // 验证第二次消息 buffer 中的 plateNo 被复用
        service.flushBuffer();
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/vehicles"), captor.capture());
        @SuppressWarnings("unchecked")
        List<VehicleUpdateMessage> vehicles = (List<VehicleUpdateMessage>) captor.getValue().get("vehicles");
        assertNotNull(vehicles);
        assertEquals(1, vehicles.size());
        assertEquals("沪A12345", vehicles.get(0).getPlateNo());
    }

    @Test
    void handleTelemetry_plateNo_nullVehicle_returnsEmpty() {
        String payload = "{\"ts\":\"2026-06-01T08:30:00.000Z\",\"lng\":121.473701,\"lat\":31.230416,\"speed\":42.5,\"heading\":90.0,\"battery\":78.3}";

        Message<String> msg = new GenericMessage<>(payload,
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/99/telemetry"));

        when(vehicleMapper.selectById(99L)).thenReturn(null);

        service.handleTelemetry(msg);
        service.flushBuffer();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/vehicles"), captor.capture());
        @SuppressWarnings("unchecked")
        List<VehicleUpdateMessage> vehicles = (List<VehicleUpdateMessage>) captor.getValue().get("vehicles");
        assertNotNull(vehicles);
        assertEquals(1, vehicles.size());
        assertEquals("", vehicles.get(0).getPlateNo());
    }

    @Test
    void handleTelemetry_plateNo_nullPlateNo_returnsEmpty() {
        String payload = "{\"ts\":\"2026-06-01T08:30:00.000Z\",\"lng\":121.473701,\"lat\":31.230416,\"speed\":42.5,\"heading\":90.0,\"battery\":78.3}";

        Message<String> msg = new GenericMessage<>(payload,
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/2/telemetry"));

        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNo(null);
        when(vehicleMapper.selectById(2L)).thenReturn(vehicle);

        service.handleTelemetry(msg);
        service.flushBuffer();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/vehicles"), captor.capture());
        @SuppressWarnings("unchecked")
        List<VehicleUpdateMessage> vehicles = (List<VehicleUpdateMessage>) captor.getValue().get("vehicles");
        assertNotNull(vehicles);
        assertEquals(1, vehicles.size());
        assertEquals("", vehicles.get(0).getPlateNo());
    }

    @Test
    void handleTelemetry_plateNo_mapperThrows_returnsEmptyAndDoesNotPropagate() {
        String payload = "{\"ts\":\"2026-06-01T08:30:00.000Z\",\"lng\":121.473701,\"lat\":31.230416,\"speed\":42.5,\"heading\":90.0,\"battery\":78.3}";

        Message<String> msg = new GenericMessage<>(payload,
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/3/telemetry"));

        when(vehicleMapper.selectById(3L)).thenThrow(new RuntimeException("DB error"));

        // 不应抛出异常
        assertDoesNotThrow(() -> service.handleTelemetry(msg));

        service.flushBuffer();
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/vehicles"), captor.capture());
        @SuppressWarnings("unchecked")
        List<VehicleUpdateMessage> vehicles = (List<VehicleUpdateMessage>) captor.getValue().get("vehicles");
        assertNotNull(vehicles);
        assertEquals(1, vehicles.size());
        assertEquals("", vehicles.get(0).getPlateNo());
    }

    @Test
    void handleTelemetry_invalidLng_rejected() {
        String payload = "{\"ts\":\"2026-06-01T08:30:00.000Z\",\"lng\":999.0,\"lat\":31.0,\"speed\":42.5,\"heading\":90.0,\"battery\":78.3}";

        Message<String> msg = new GenericMessage<>(payload,
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/1/telemetry"));

        service.handleTelemetry(msg);

        // 不应写入 DB（校验拒绝）
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(vehicleMapper, never()).selectById(any());
    }

    @Test
    void handleTelemetry_invalidJson_skipped() {
        Message<String> msg = new GenericMessage<>("not json",
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/1/telemetry"));

        service.handleTelemetry(msg);
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        verify(vehicleMapper, never()).selectById(any());
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
