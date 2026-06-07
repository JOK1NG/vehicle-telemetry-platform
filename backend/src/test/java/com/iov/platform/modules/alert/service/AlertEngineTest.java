package com.iov.platform.modules.alert.service;

import com.iov.platform.modules.alert.entity.AlertRule;
import com.iov.platform.modules.geofence.service.GeofenceEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * AlertEngine 单元测试
 * 覆盖：evaluateTelemetry（超速/低电/围栏/离线状态更新）、evaluateGeofence（进出状态机）、scanOffline（离线检测逻辑）
 */
@SuppressWarnings({"null", "unchecked"})
class AlertEngineTest {

    private AlertService alertService;
    private GeofenceEvaluator geofenceEvaluator;
    private StringRedisTemplate redis;
    private AlertEngine engine;

    private SetOperations<String, String> setOps;
    private HashOperations<String, Object, Object> hashOps;
    private ValueOperations<String, String> valOps;

    @BeforeEach
    void setUp() {
        alertService = mock(AlertService.class);
        geofenceEvaluator = mock(GeofenceEvaluator.class);
        redis = mock(StringRedisTemplate.class);

        setOps = mock(SetOperations.class);
        hashOps = mock(HashOperations.class);
        valOps = mock(ValueOperations.class);

        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForHash()).thenReturn(hashOps);
        when(redis.opsForValue()).thenReturn(valOps);

        engine = new AlertEngine(alertService, geofenceEvaluator, redis);
    }

    // ===== evaluateTelemetry =====

    @Test
    void evaluateTelemetry_statusZero_skipsEvaluation() {
        // status=0（离线）不应触发任何检测
        engine.evaluateTelemetry(1L, 121.0, 31.0, 100.0, 10.0, 0);
        verify(alertService, never()).fireAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void evaluateTelemetry_overspeed_firesAlert() {
        AlertRule rule = new AlertRule();
        rule.setId(1L);
        rule.setCode("OVERSPEED");
        rule.setEnabled(true);
        rule.setThreshold(80.0);
        when(alertService.getRule("OVERSPEED")).thenReturn(rule);
        when(alertService.getRule("LOW_BATTERY")).thenReturn(null);
        when(geofenceEvaluator.findContainingGeofenceIds(anyLong(), anyDouble(), anyDouble())).thenReturn(Set.of());

        engine.evaluateTelemetry(1L, 121.0, 31.0, 100.0, 50.0, 1);

        verify(alertService).fireAlert(eq(1L), eq("OVERSPEED"), contains("100.0"), eq(121.0), eq(31.0), eq(1L), isNull());
    }

    @Test
    void evaluateTelemetry_lowBattery_firesAlert() {
        AlertRule rule = new AlertRule();
        rule.setId(2L);
        rule.setCode("LOW_BATTERY");
        rule.setEnabled(true);
        rule.setThreshold(20.0);
        when(alertService.getRule("OVERSPEED")).thenReturn(null);
        when(alertService.getRule("LOW_BATTERY")).thenReturn(rule);
        when(geofenceEvaluator.findContainingGeofenceIds(anyLong(), anyDouble(), anyDouble())).thenReturn(Set.of());

        engine.evaluateTelemetry(1L, 121.0, 31.0, 50.0, 10.0, 1);

        verify(alertService).fireAlert(eq(1L), eq("LOW_BATTERY"), contains("10%"), eq(121.0), eq(31.0), eq(2L), isNull());
    }

    @Test
    void evaluateTelemetry_ruleDisabled_noAlert() {
        AlertRule rule = new AlertRule();
        rule.setId(1L);
        rule.setCode("OVERSPEED");
        rule.setEnabled(false);
        rule.setThreshold(80.0);
        when(alertService.getRule("OVERSPEED")).thenReturn(rule);
        when(alertService.getRule("LOW_BATTERY")).thenReturn(null);
        when(geofenceEvaluator.findContainingGeofenceIds(anyLong(), anyDouble(), anyDouble())).thenReturn(Set.of());

        engine.evaluateTelemetry(1L, 121.0, 31.0, 100.0, 50.0, 1);

        verify(alertService, never()).fireAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void evaluateTelemetry_ruleNull_noAlert() {
        when(alertService.getRule("OVERSPEED")).thenReturn(null);
        when(alertService.getRule("LOW_BATTERY")).thenReturn(null);
        when(geofenceEvaluator.findContainingGeofenceIds(anyLong(), anyDouble(), anyDouble())).thenReturn(Set.of());

        engine.evaluateTelemetry(1L, 121.0, 31.0, 100.0, 50.0, 1);

        verify(alertService, never()).fireAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void evaluateTelemetry_belowThreshold_noAlert() {
        AlertRule rule = new AlertRule();
        rule.setId(1L);
        rule.setCode("OVERSPEED");
        rule.setEnabled(true);
        rule.setThreshold(80.0);
        when(alertService.getRule("OVERSPEED")).thenReturn(rule);
        when(alertService.getRule("LOW_BATTERY")).thenReturn(null);
        when(geofenceEvaluator.findContainingGeofenceIds(anyLong(), anyDouble(), anyDouble())).thenReturn(Set.of());

        engine.evaluateTelemetry(1L, 121.0, 31.0, 70.0, 50.0, 1); // 70 < 80

        verify(alertService, never()).fireAlert(any(), any(), any(), any(), any(), any(), any());
    }

    // ===== evaluateGeofence 状态机 =====

    @Test
    void evaluateGeofence_enter_firesEnterAlert() {
        when(geofenceEvaluator.findContainingGeofenceIds(1L, 121.0, 31.0)).thenReturn(Set.of(10L));
        when(geofenceEvaluator.getGeofenceName(10L)).thenReturn("测试围栏");

        engine.evaluateGeofence(1L, 121.0, 31.0);

        verify(alertService).fireAlert(eq(1L), eq("GEOFENCE_ENTER"), contains("测试围栏"), eq(121.0), eq(31.0), isNull(), eq(10L));
        verify(alertService, never()).fireAlert(any(), eq("GEOFENCE_EXIT"), any(), any(), any(), any(), any());
    }

    @Test
    void evaluateGeofence_exit_firesExitAlert() {
        // 第一次：进入围栏 10
        when(geofenceEvaluator.findContainingGeofenceIds(1L, 121.0, 31.0)).thenReturn(Set.of(10L));
        when(geofenceEvaluator.getGeofenceName(10L)).thenReturn("测试围栏");
        engine.evaluateGeofence(1L, 121.0, 31.0);
        verify(alertService).fireAlert(any(), eq("GEOFENCE_ENTER"), any(), any(), any(), any(), any());

        // 第二次：离开围栏 10
        when(geofenceEvaluator.findContainingGeofenceIds(1L, 121.0, 31.0)).thenReturn(Set.of());
        when(geofenceEvaluator.appliesToVehicle(10L, 1L)).thenReturn(true);
        engine.evaluateGeofence(1L, 121.0, 31.0);

        verify(alertService).fireAlert(eq(1L), eq("GEOFENCE_EXIT"), contains("测试围栏"), eq(121.0), eq(31.0), isNull(), eq(10L));
    }

    @Test
    void evaluateGeofence_bindingNoLongerApplies_clearsStateWithoutExitAlert() {
        // 第一次：车辆在围栏 10 内
        when(geofenceEvaluator.findContainingGeofenceIds(1L, 121.0, 31.0)).thenReturn(Set.of(10L));
        when(geofenceEvaluator.getGeofenceName(10L)).thenReturn("测试围栏");
        engine.evaluateGeofence(1L, 121.0, 31.0);
        verify(alertService).fireAlert(any(), eq("GEOFENCE_ENTER"), any(), any(), any(), any(), any());
        clearInvocations(alertService);

        // 第二次：管理员移除了该车绑定，不应被当作车辆离开围栏
        when(geofenceEvaluator.findContainingGeofenceIds(1L, 121.0, 31.0)).thenReturn(Set.of());
        when(geofenceEvaluator.appliesToVehicle(10L, 1L)).thenReturn(false);
        engine.evaluateGeofence(1L, 121.0, 31.0);

        verify(alertService, never()).fireAlert(any(), eq("GEOFENCE_EXIT"), any(), any(), any(), any(), any());
    }

    @Test
    void evaluateGeofence_noChange_noAlert() {
        // 连续两次都在同一个围栏内
        when(geofenceEvaluator.findContainingGeofenceIds(1L, 121.0, 31.0)).thenReturn(Set.of(10L));
        when(geofenceEvaluator.getGeofenceName(10L)).thenReturn("测试围栏");

        engine.evaluateGeofence(1L, 121.0, 31.0); // 进入
        engine.evaluateGeofence(1L, 121.0, 31.0); // 仍在内部

        // 只有第一次触发 ENTER
        verify(alertService, times(1)).fireAlert(any(), eq("GEOFENCE_ENTER"), any(), any(), any(), any(), any());
        verify(alertService, never()).fireAlert(any(), eq("GEOFENCE_EXIT"), any(), any(), any(), any(), any());
    }

    @Test
    void evaluateGeofence_switchGeofence_enterAndExit() {
        // 进入围栏 10
        when(geofenceEvaluator.findContainingGeofenceIds(1L, 121.0, 31.0)).thenReturn(Set.of(10L));
        when(geofenceEvaluator.getGeofenceName(10L)).thenReturn("围栏A");
        engine.evaluateGeofence(1L, 121.0, 31.0);

        // 同时进入 20，离开 10
        when(geofenceEvaluator.findContainingGeofenceIds(1L, 121.0, 31.0)).thenReturn(Set.of(20L));
        when(geofenceEvaluator.appliesToVehicle(10L, 1L)).thenReturn(true);
        when(geofenceEvaluator.getGeofenceName(20L)).thenReturn("围栏B");
        engine.evaluateGeofence(1L, 121.0, 31.0);

        verify(alertService).fireAlert(any(), eq("GEOFENCE_ENTER"), contains("围栏A"), any(), any(), any(), any());
        verify(alertService).fireAlert(any(), eq("GEOFENCE_EXIT"), contains("围栏A"), any(), any(), any(), any());
        verify(alertService).fireAlert(any(), eq("GEOFENCE_ENTER"), contains("围栏B"), any(), any(), any(), any());
    }

    // ===== scanOffline（反射调用） =====

    private void invokeScanOffline() throws Exception {
        Method m = AlertEngine.class.getDeclaredMethod("scanOffline");
        m.setAccessible(true);
        m.invoke(engine);
    }

    private void setLastSeen(Long vehicleId, Instant instant) throws Exception {
        java.lang.reflect.Field f = AlertEngine.class.getDeclaredField("lastSeen");
        f.setAccessible(true);
        java.util.concurrent.ConcurrentHashMap<Long, Instant> map =
                (java.util.concurrent.ConcurrentHashMap<Long, Instant>) f.get(engine);
        map.put(vehicleId, instant);
    }

    @Test
    void scanOffline_ruleNull_doesNothing() throws Exception {
        when(alertService.getRule("OFFLINE")).thenReturn(null);
        invokeScanOffline();
        verify(alertService, never()).fireAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scanOffline_ruleDisabled_doesNothing() throws Exception {
        AlertRule rule = new AlertRule();
        rule.setCode("OFFLINE");
        rule.setEnabled(false);
        rule.setThreshold(5.0);
        when(alertService.getRule("OFFLINE")).thenReturn(rule);
        invokeScanOffline();
        verify(alertService, never()).fireAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scanOffline_thresholdNaN_doesNothing() throws Exception {
        AlertRule rule = new AlertRule();
        rule.setCode("OFFLINE");
        rule.setEnabled(true);
        rule.setThreshold(Double.NaN);
        when(alertService.getRule("OFFLINE")).thenReturn(rule);
        invokeScanOffline();
        verify(alertService, never()).fireAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scanOffline_thresholdNegative_doesNothing() throws Exception {
        AlertRule rule = new AlertRule();
        rule.setCode("OFFLINE");
        rule.setEnabled(true);
        rule.setThreshold(-1.0);
        when(alertService.getRule("OFFLINE")).thenReturn(rule);
        invokeScanOffline();
        verify(alertService, never()).fireAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scanOffline_noLastSeen_noAlert() throws Exception {
        AlertRule rule = new AlertRule();
        rule.setId(3L);
        rule.setCode("OFFLINE");
        rule.setEnabled(true);
        rule.setThreshold(5.0);
        when(alertService.getRule("OFFLINE")).thenReturn(rule);
        when(setOps.members("vehicle:online")).thenReturn(Set.of("1"));

        invokeScanOffline();

        // 没有记录过 lastSeen，不应告警
        verify(alertService, never()).fireAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scanOffline_elapsedExceedsThreshold_firesOfflineAlert() throws Exception {
        // 直接设置 lastSeen 为 10 分钟前，避免依赖真实时间流逝
        setLastSeen(1L, Instant.now().minusSeconds(600));

        AlertRule rule = new AlertRule();
        rule.setId(3L);
        rule.setCode("OFFLINE");
        rule.setEnabled(true);
        rule.setThreshold(5.0); // 5 分钟
        when(alertService.getRule("OFFLINE")).thenReturn(rule);
        when(setOps.members("vehicle:online")).thenReturn(Set.of("1"));
        when(hashOps.entries("vehicle:rt:1")).thenReturn(Map.of()); // 空 = 无实时数据
        when(valOps.get("vehicle:meta:1")).thenReturn("沪A12345,1");

        invokeScanOffline();

        verify(alertService).fireAlert(eq(1L), eq("OFFLINE"), contains("沪A12345"), isNull(), isNull(), eq(3L), isNull());
    }

    @Test
    void scanOffline_freshRedisData_resetsLastSeen() throws Exception {
        // 直接设置 lastSeen 为 10 分钟前
        setLastSeen(1L, Instant.now().minusSeconds(600));

        AlertRule rule = new AlertRule();
        rule.setId(3L);
        rule.setCode("OFFLINE");
        rule.setEnabled(true);
        rule.setThreshold(5.0); // 5 分钟阈值
        when(alertService.getRule("OFFLINE")).thenReturn(rule);
        when(setOps.members("vehicle:online")).thenReturn(Set.of("1"));
        // Redis 中有新鲜数据（1 分钟前的 ts）
        String recentTs = Instant.now().minusSeconds(60).toString();
        when(hashOps.entries("vehicle:rt:1")).thenReturn(Map.of("ts", recentTs));
        when(valOps.get("vehicle:meta:1")).thenReturn(null);

        invokeScanOffline();

        // 不应触发离线告警，因为 rtData ts 新鲜（60s < 300s），重置了 lastSeen
        verify(alertService, never()).fireAlert(any(), eq("OFFLINE"), any(), any(), any(), any(), any());
    }

    @Test
    void scanOffline_staleRedisData_firesOfflineAlert() throws Exception {
        // 直接设置 lastSeen 为 10 分钟前
        setLastSeen(1L, Instant.now().minusSeconds(600));

        AlertRule rule = new AlertRule();
        rule.setId(3L);
        rule.setCode("OFFLINE");
        rule.setEnabled(true);
        rule.setThreshold(5.0); // 5 分钟阈值
        when(alertService.getRule("OFFLINE")).thenReturn(rule);
        when(setOps.members("vehicle:online")).thenReturn(Set.of("1"));
        // Redis 中有陈旧数据（10 分钟前的 ts）
        String staleTs = Instant.now().minusSeconds(600).toString();
        when(hashOps.entries("vehicle:rt:1")).thenReturn(Map.of("ts", staleTs));
        when(valOps.get("vehicle:meta:1")).thenReturn(null);

        invokeScanOffline();

        // 应触发离线告警，因为 rtData ts 也陈旧（600s >= 300s）
        verify(alertService).fireAlert(any(), eq("OFFLINE"), any(), isNull(), isNull(), eq(3L), isNull());
    }

    @Test
    void scanOffline_invalidVehicleId_ignored() throws Exception {
        AlertRule rule = new AlertRule();
        rule.setId(3L);
        rule.setCode("OFFLINE");
        rule.setEnabled(true);
        rule.setThreshold(5.0);
        when(alertService.getRule("OFFLINE")).thenReturn(rule);
        when(setOps.members("vehicle:online")).thenReturn(Set.of("not-a-number"));

        invokeScanOffline();

        verify(alertService, never()).fireAlert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void scanOffline_nullMeta_plateNoEmpty() throws Exception {
        setLastSeen(1L, Instant.now().minusSeconds(600));

        AlertRule rule = new AlertRule();
        rule.setId(3L);
        rule.setCode("OFFLINE");
        rule.setEnabled(true);
        rule.setThreshold(5.0);
        when(alertService.getRule("OFFLINE")).thenReturn(rule);
        when(setOps.members("vehicle:online")).thenReturn(Set.of("1"));
        when(hashOps.entries("vehicle:rt:1")).thenReturn(Map.of());
        when(valOps.get("vehicle:meta:1")).thenReturn(null);

        invokeScanOffline();

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(alertService).fireAlert(eq(1L), eq("OFFLINE"), msgCaptor.capture(), isNull(), isNull(), eq(3L), isNull());
        assertTrue(msgCaptor.getValue().contains("车辆离线"));
    }
}
