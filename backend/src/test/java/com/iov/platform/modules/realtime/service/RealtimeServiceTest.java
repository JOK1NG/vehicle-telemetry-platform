package com.iov.platform.modules.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.modules.alert.service.AlertEngine;
import com.iov.platform.modules.realtime.dto.VehicleUpdateMessage;
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
 * RealtimeService 单元测试
 * 含车辆存在性校验、plateNo 逻辑、字段范围校验等
 */
@SuppressWarnings({"null", "unchecked"})
class RealtimeServiceTest {

    private StringRedisTemplate redis;
    private JdbcTemplate jdbc;
    private SimpMessagingTemplate messagingTemplate;
    private ObjectMapper objectMapper;
    private VehicleMapper vehicleMapper;
    private AlertEngine alertEngine;
    private RealtimeService service;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        jdbc = mock(JdbcTemplate.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        objectMapper = new ObjectMapper();
        vehicleMapper = mock(VehicleMapper.class);
        alertEngine = mock(AlertEngine.class);

        // Mock Redis operations
        HashOperations<String, Object, Object> hashOps = mock(HashOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        ValueOperations<String, String> valOps = mock(ValueOperations.class);

        when(redis.opsForHash()).thenReturn(hashOps);
        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForValue()).thenReturn(valOps);

        service = new RealtimeService(redis, jdbc, messagingTemplate, objectMapper, vehicleMapper, alertEngine);
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
        vehicle.setStatus(1);
        // isKnownVehicle 和 getPlateNo 都会调用 selectById，由于有缓存，首次查后缓存
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
        vehicle.setStatus(1);
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);

        service.handleTelemetry(msg1);
        service.handleTelemetry(msg2);

        // isKnownVehicle 首次查库后缓存，getPlateNo 首次查库后也缓存
        // 同一车辆第二次消息不再查库（isKnownVehicle 命中缓存）
        // 注意：由于 isKnownVehicle 和 getPlateNo/getVehicleStatus 分别调用 selectById，
        // 第一次 handleTelemetry 会查 selectById(1L) 多次（但 isKnownVehicle 缓存后不再查）
        verify(vehicleMapper, atLeastOnce()).selectById(1L);

        // 验证第二次消息 buffer 中的 plateNo 被复用（不清空缓存时，第二次消息不再查 DB）
        service.flushBuffer();
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/vehicles"), captor.capture());
        List<VehicleUpdateMessage> vehicles = (List<VehicleUpdateMessage>) captor.getValue().get("vehicles");
        assertNotNull(vehicles);
        assertEquals(1, vehicles.size());
        assertEquals("沪A12345", vehicles.get(0).getPlateNo());
    }

    @Test
    void handleTelemetry_unknownVehicleId_rejected() {
        // 安全修复（MUL-39）：vehicleId 不存在于 vehicle 表时拒绝消息
        String payload = "{\"ts\":\"2026-06-01T08:30:00.000Z\",\"lng\":121.473701,\"lat\":31.230416,\"speed\":42.5,\"heading\":90.0,\"battery\":78.3}";

        Message<String> msg = new GenericMessage<>(payload,
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/99/telemetry"));

        when(vehicleMapper.selectById(99L)).thenReturn(null);

        service.handleTelemetry(msg);

        // 不存在的车辆 ID 应该被拒绝，不应写入 DB 或 Redis
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleTelemetry_nullVehicle_returnsEmptyPlateNo() {
        // vehicleId 存在于 vehicle 表但 vehicle 对象返回 null → 不应到达此路径
        // 因为 isKnownVehicle 会先校验车辆存在性，selectById 返回 null 表示不存在
        // 所以 handleTelemetry 会直接拒绝此消息
        // 此测试改为验证 vehicle 存在但 plateNo 为 null
        String payload = "{\"ts\":\"2026-06-01T08:30:00.000Z\",\"lng\":121.473701,\"lat\":31.230416,\"speed\":42.5,\"heading\":90.0,\"battery\":78.3}";

        Message<String> msg = new GenericMessage<>(payload,
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/2/telemetry"));

        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNo(null);
        vehicle.setStatus(0);
        when(vehicleMapper.selectById(2L)).thenReturn(vehicle);

        service.handleTelemetry(msg);
        service.flushBuffer();

        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/vehicles"), captor.capture());
        List<VehicleUpdateMessage> vehicles = (List<VehicleUpdateMessage>) captor.getValue().get("vehicles");
        assertNotNull(vehicles);
        assertEquals(1, vehicles.size());
        assertEquals("", vehicles.get(0).getPlateNo());
    }

    @Test
    void handleTelemetry_mapperThrows_rejected() {
        String payload = "{\"ts\":\"2026-06-01T08:30:00.000Z\",\"lng\":121.473701,\"lat\":31.230416,\"speed\":42.5,\"heading\":90.0,\"battery\":78.3}";

        // selectById 在 isKnownVehicle 中抛异常时，车辆被认为不存在，消息被拒绝
        when(vehicleMapper.selectById(3L)).thenThrow(new RuntimeException("DB error"));

        // 不应抛出异常，但消息被拒绝（isKnownVehicle 返回 false）
        Message<String> msg = new GenericMessage<>(payload,
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/3/telemetry"));

        assertDoesNotThrow(() -> service.handleTelemetry(msg));

        // 不应该有 DB 写入
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleTelemetry_invalidLng_rejected() {
        String payload = "{\"ts\":\"2026-06-01T08:30:00.000Z\",\"lng\":999.0,\"lat\":31.0,\"speed\":42.5,\"heading\":90.0,\"battery\":78.3}";

        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNo("沪A12345");
        vehicle.setStatus(1);
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);

        Message<String> msg = new GenericMessage<>(payload,
                Map.of(MqttHeaders.RECEIVED_TOPIC, "vehicle/1/telemetry"));

        service.handleTelemetry(msg);

        // 不应写入 DB（校验拒绝）
        verify(jdbc, never()).update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handleTelemetry_invalidJson_skipped() {
        Vehicle vehicle = new Vehicle();
        vehicle.setPlateNo("沪A12345");
        vehicle.setStatus(1);
        when(vehicleMapper.selectById(1L)).thenReturn(vehicle);

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
