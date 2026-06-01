package com.iov.platform.simulator.model;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VehicleSimState 运动模型边界测试。覆盖 MUL-35 Reviewer 提的 3 个未覆盖场景：
 *   - heading 跨 360→0 始终落在 [0, 360)
 *   - battery <= 5 时 step 不再移动
 *   - 越界时调头且不飞出 spread 框
 */
class VehicleSimStateTest {

    private static final double CX = 121.473701;
    private static final double CY = 31.230416;
    private static final double SX = 0.05;
    private static final double SY = 0.045;

    private static VehicleSimState newState(long id, double initLng, double initLat) {
        return new VehicleSimState(id, initLng, initLat, CX, CY, SX, SY, 20.0, 60.0);
    }

    @Test
    void headingStaysWithinZeroThreeSixty() {
        // 强制把初始 heading 设在 358 附近，验证多次 step 不会变成 361、-1 之类
        VehicleSimState s = newState(1L, CX, CY);
        forceField(s, "heading", 358.5);
        for (int i = 0; i < 200; i++) {
            s.step(1000);
            double h = s.getHeading();
            assertTrue(h >= 0.0 && h < 360.0,
                    "heading 越界 step=" + i + " heading=" + h);
        }
    }

    @Test
    void batteryDepletedStopsMovement() throws Exception {
        VehicleSimState s = newState(2L, CX, CY);
        double initLng = s.getLng();
        double initLat = s.getLat();
        double initHeading = s.getHeading();
        // 反射把 battery 设到 5.0 以下，模拟耗尽
        forceField(s, "battery", 4.0);

        for (int i = 0; i < 100; i++) {
            s.step(1000);
        }
        assertEquals(initLng, s.getLng(), 1e-9, "电量耗尽后经度应不变");
        assertEquals(initLat, s.getLat(), 1e-9, "电量耗尽后纬度应不变");
        assertEquals(initHeading, s.getHeading(), 1e-9, "电量耗尽后航向应不变");
    }

    @Test
    void outOfBoundsTriggersHeadingTurn() throws Exception {
        // 初始点紧贴右边界，heading 90° 沿 +lng 必越界
        VehicleSimState s = newState(3L, CX + SX - 1e-7, CY);
        forceField(s, "heading", 90.0);   // 90° = 沿 +lng 方向，必越界
        forceField(s, "speed", 60.0);     // 最大速度，跨大步越界
        double initLng = s.getLng();
        double initLat = s.getLat();
        double initHeading = s.getHeading();

        s.step(60_000);  // 1 分钟，确保单步就跨出 spread
        // 越界：heading 应被改写（180° ± 45°），位置不变
        assertEquals(initLng, s.getLng(), 1e-9, "越界时经度应保持");
        assertEquals(initLat, s.getLat(), 1e-9, "越界时纬度应保持");
        assertNotEquals(initHeading, s.getHeading(), "越界后航向应被重写");
        double h = s.getHeading();
        // 180° ± 45° + 90° = 270° ± 45°，即大致 [225, 315]
        assertTrue(h >= 225.0 && h <= 315.0,
                "调头后航向应在 180° ± 45° 翻转附近，实际=" + h);
    }

    private static void forceField(VehicleSimState s, String name, double value) {
        try {
            Field f = VehicleSimState.class.getDeclaredField(name);
            f.setAccessible(true);
            f.setDouble(s, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("反射设置 " + name + " 失败", e);
        }
    }
}
