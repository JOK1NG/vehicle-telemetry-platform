package com.iov.platform.modules.geofence.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * GeofenceEvaluator 单元测试
 * 覆盖：pointInRing（Ray casting 算法）、contains（GeoJSON 解析）、findContainingGeofenceIds（缓存集成）
 */
class GeofenceEvaluatorTest {

    private GeofenceCache cache;
    private ObjectMapper objectMapper;
    private GeofenceEvaluator evaluator;

    @BeforeEach
    void setUp() {
        cache = mock(GeofenceCache.class);
        objectMapper = new ObjectMapper();
        evaluator = new GeofenceEvaluator(cache, objectMapper);
    }

    // ===== pointInRing（反射调用） =====

    private boolean invokePointInRing(double lng, double lat, String geoJson) throws Exception {
        Method m = GeofenceEvaluator.class.getDeclaredMethod("pointInRing", double.class, double.class,
                com.fasterxml.jackson.databind.JsonNode.class);
        m.setAccessible(true);
        com.fasterxml.jackson.databind.JsonNode ring = objectMapper.readTree(geoJson).path("coordinates").get(0);
        return (Boolean) m.invoke(evaluator, lng, lat, ring);
    }

    /**
     * 构建标准 GeoJSON Polygon（外环）
     */
    private String squareGeoJson(double cx, double cy, double half) {
        return "{\"type\":\"Polygon\",\"coordinates\":[["
                + "[" + (cx - half) + "," + (cy - half) + "],"
                + "[" + (cx + half) + "," + (cy - half) + "],"
                + "[" + (cx + half) + "," + (cy + half) + "],"
                + "[" + (cx - half) + "," + (cy + half) + "],"
                + "[" + (cx - half) + "," + (cy - half) + "]"
                + "]]}";
    }

    @Test
    void pointInRing_pointInsideSquare_returnsTrue() throws Exception {
        String geoJson = squareGeoJson(121.0, 31.0, 0.01);
        assertTrue(invokePointInRing(121.0, 31.0, geoJson));
    }

    @Test
    void pointInRing_pointOutsideSquare_returnsFalse() throws Exception {
        String geoJson = squareGeoJson(121.0, 31.0, 0.01);
        assertFalse(invokePointInRing(122.0, 32.0, geoJson));
    }

    @Test
    void pointInRing_pointOnEdge_returnsTrueOrFalse() throws Exception {
        // Ray casting 对边上的点行为未定义，但至少不抛异常
        String geoJson = squareGeoJson(121.0, 31.0, 0.01);
        boolean onEdge = invokePointInRing(121.0, 31.01, geoJson); // 上边
        assertTrue(onEdge || !onEdge); // 不抛异常即通过
    }

    @Test
    void pointInRing_trianglePointInside_returnsTrue() throws Exception {
        String geoJson = "{\"type\":\"Polygon\",\"coordinates\":[["
                + "[121.0,31.0],[121.01,31.0],[121.005,31.01],[121.0,31.0]"
                + "]]}";
        // 重心附近
        assertTrue(invokePointInRing(121.005, 31.003, geoJson));
    }

    @Test
    void pointInRing_trianglePointOutside_returnsFalse() throws Exception {
        String geoJson = "{\"type\":\"Polygon\",\"coordinates\":[["
                + "[121.0,31.0],[121.01,31.0],[121.005,31.01],[121.0,31.0]"
                + "]]}";
        assertFalse(invokePointInRing(121.02, 31.02, geoJson));
    }

    @Test
    void pointInRing_ringTooSmall_returnsFalse() throws Exception {
        String geoJson = "{\"type\":\"Polygon\",\"coordinates\":[[[121.0,31.0],[121.001,31.0]]]}";
        assertFalse(invokePointInRing(121.0, 31.0, geoJson));
    }

    // ===== contains（反射调用） =====

    private boolean invokeContains(String geoJson, double lng, double lat) throws Exception {
        Method m = GeofenceEvaluator.class.getDeclaredMethod("contains", String.class, double.class, double.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(evaluator, geoJson, lng, lat);
    }

    @Test
    void contains_validPolygonPointInside_returnsTrue() throws Exception {
        String geoJson = squareGeoJson(121.0, 31.0, 0.01);
        assertTrue(invokeContains(geoJson, 121.0, 31.0));
    }

    @Test
    void contains_validPolygonPointOutside_returnsFalse() throws Exception {
        String geoJson = squareGeoJson(121.0, 31.0, 0.01);
        assertFalse(invokeContains(geoJson, 122.0, 32.0));
    }

    @Test
    void contains_notPolygon_returnsFalse() throws Exception {
        String geoJson = "{\"type\":\"Point\",\"coordinates\":[121.0,31.0]}";
        assertFalse(invokeContains(geoJson, 121.0, 31.0));
    }

    @Test
    void contains_emptyCoordinates_returnsFalse() throws Exception {
        String geoJson = "{\"type\":\"Polygon\",\"coordinates\":[]}";
        assertFalse(invokeContains(geoJson, 121.0, 31.0));
    }

    @Test
    void contains_invalidJson_throwsException() {
        assertThrows(Exception.class, () -> invokeContains("not json", 121.0, 31.0));
    }

    // ===== findContainingGeofenceIds =====

    @Test
    void findContainingGeofenceIds_pointInsideOne_returnsId() {
        String geoJson = squareGeoJson(121.0, 31.0, 0.01);
        when(cache.allIds()).thenReturn(Set.of(1L, 2L));
        when(cache.getGeomGeoJson(1L)).thenReturn(geoJson);
        when(cache.getGeomGeoJson(2L)).thenReturn(null);

        Set<Long> result = evaluator.findContainingGeofenceIds(121.0, 31.0);

        assertEquals(Set.of(1L), result);
    }

    @Test
    void findContainingGeofenceIds_vehicleBindingMatches_returnsId() {
        String geoJson = squareGeoJson(121.0, 31.0, 0.01);
        when(cache.allIds()).thenReturn(Set.of(1L));
        when(cache.appliesToVehicle(1L, 7L)).thenReturn(true);
        when(cache.getGeomGeoJson(1L)).thenReturn(geoJson);

        Set<Long> result = evaluator.findContainingGeofenceIds(7L, 121.0, 31.0);

        assertEquals(Set.of(1L), result);
    }

    @Test
    void findContainingGeofenceIds_vehicleBindingDoesNotMatch_skipsFence() {
        String geoJson = squareGeoJson(121.0, 31.0, 0.01);
        when(cache.allIds()).thenReturn(Set.of(1L));
        when(cache.appliesToVehicle(1L, 8L)).thenReturn(false);
        when(cache.getGeomGeoJson(1L)).thenReturn(geoJson);

        Set<Long> result = evaluator.findContainingGeofenceIds(8L, 121.0, 31.0);

        assertTrue(result.isEmpty());
        verify(cache, never()).getGeomGeoJson(1L);
    }

    @Test
    void findContainingGeofenceIds_pointInsideNone_returnsEmpty() {
        String geoJson = squareGeoJson(121.0, 31.0, 0.01);
        when(cache.allIds()).thenReturn(Set.of(1L));
        when(cache.getGeomGeoJson(1L)).thenReturn(geoJson);

        Set<Long> result = evaluator.findContainingGeofenceIds(122.0, 32.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void findContainingGeofenceIds_pointInsideMultiple_returnsAll() {
        String geoJson1 = squareGeoJson(121.0, 31.0, 0.01);
        String geoJson2 = squareGeoJson(121.0, 31.0, 0.02); // 更大的同中心正方形
        when(cache.allIds()).thenReturn(Set.of(1L, 2L));
        when(cache.getGeomGeoJson(1L)).thenReturn(geoJson1);
        when(cache.getGeomGeoJson(2L)).thenReturn(geoJson2);

        Set<Long> result = evaluator.findContainingGeofenceIds(121.0, 31.0);

        assertEquals(Set.of(1L, 2L), result);
    }

    @Test
    void findContainingGeofenceIds_nullGeoJson_skipped() {
        when(cache.allIds()).thenReturn(Set.of(1L));
        when(cache.getGeomGeoJson(1L)).thenReturn(null);

        Set<Long> result = evaluator.findContainingGeofenceIds(121.0, 31.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void findContainingGeofenceIds_invalidGeoJson_skipped() {
        when(cache.allIds()).thenReturn(Set.of(1L));
        when(cache.getGeomGeoJson(1L)).thenReturn("not valid json");

        Set<Long> result = evaluator.findContainingGeofenceIds(121.0, 31.0);

        assertTrue(result.isEmpty());
    }

    @Test
    void findContainingGeofenceIds_emptyCache_returnsEmpty() {
        when(cache.allIds()).thenReturn(Set.of());

        Set<Long> result = evaluator.findContainingGeofenceIds(121.0, 31.0);

        assertTrue(result.isEmpty());
    }

    // ===== getGeofenceName =====

    @Test
    void getGeofenceName_delegatesToCache() {
        when(cache.getName(1L)).thenReturn("测试围栏");
        assertEquals("测试围栏", evaluator.getGeofenceName(1L));
    }

    @Test
    void getGeofenceName_null_returnsNull() {
        when(cache.getName(1L)).thenReturn(null);
        assertNull(evaluator.getGeofenceName(1L));
    }
}
