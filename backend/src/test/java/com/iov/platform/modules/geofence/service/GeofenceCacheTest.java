package com.iov.platform.modules.geofence.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GeofenceCacheTest {

    @Test
    void appliesToVehicle_emptyBindingAppliesToAllVehicles() {
        GeofenceCache cache = new GeofenceCache();
        cache.load(List.of(Map.of(
                "id", 1L,
                "name", "全车围栏",
                "geom_text", "{}",
                "vehicle_ids", List.of()
        )));

        assertTrue(cache.appliesToVehicle(1L, 10L));
        assertTrue(cache.appliesToVehicle(1L, 20L));
    }

    @Test
    void appliesToVehicle_nonEmptyBindingOnlyAppliesToBoundVehicle() {
        GeofenceCache cache = new GeofenceCache();
        cache.load(List.of(Map.of(
                "id", 1L,
                "name", "指定车辆围栏",
                "geom_text", "{}",
                "vehicle_ids", List.of(10L, 20L)
        )));

        assertTrue(cache.appliesToVehicle(1L, 10L));
        assertTrue(cache.appliesToVehicle(1L, 20L));
        assertFalse(cache.appliesToVehicle(1L, 30L));
    }
}
