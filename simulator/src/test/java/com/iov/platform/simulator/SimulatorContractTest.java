package com.iov.platform.simulator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.simulator.model.TelemetryPayload;
import com.iov.platform.simulator.model.VehicleSimState;
import com.iov.platform.simulator.route.RouteProvider;
import com.iov.platform.simulator.runner.SimulatorRunner;
import com.iov.platform.simulator.config.SimulatorProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 模拟器契约一致性测试：验证 payload 字段名、类型、topic 模板与
 * docs/mvp-00-implementation-contract.md §2.1 / §2.2 完全一致。
 */
class SimulatorContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void topicTemplateMatchesContract() {
        // 契约 §2.1：vehicle/{vehicleId}/telemetry
        String topic = String.format(SimulatorRunner.TOPIC_TEMPLATE, 7L);
        assertEquals("vehicle/7/telemetry", topic);
    }

    @Test
    void payloadFieldsMatchContract() throws Exception {
        TelemetryPayload payload = new TelemetryPayload(
                "2026-06-01T08:30:00.000Z",
                121.473701,
                31.230416,
                42.5,
                90.0,
                78.3,
                null
        );
        String json = objectMapper.writeValueAsString(payload);

        // 序列化字段集合（与契约表格一致）
        JsonNode node = objectMapper.readTree(json);
        assertTrue(node.has("ts"), "ts 字段必填");
        assertTrue(node.has("lng"), "lng 字段必填");
        assertTrue(node.has("lat"), "lat 字段必填");
        assertTrue(node.has("speed"), "speed 字段必填");
        assertTrue(node.has("heading"), "heading 字段必填");
        assertTrue(node.has("battery"), "battery 字段必填");
        assertTrue(node.has("faultCode"), "faultCode 字段存在");
        // 故意多塞的字段应当被忽略
        assertEquals(7, node.size(), "payload 字段数量与契约 §2.2 一致");

        // 类型校验
        assertTrue(node.get("ts").isTextual());
        assertTrue(node.get("lng").isNumber());
        assertTrue(node.get("lat").isNumber());
        assertTrue(node.get("speed").isNumber());
        assertTrue(node.get("heading").isNumber());
        assertTrue(node.get("battery").isNumber());
        assertTrue(node.get("faultCode").isNull(), "无故障时传 null");

        // 故障码字符串值
        payload.setFaultCode("P0100");
        JsonNode withFault = objectMapper.readTree(objectMapper.writeValueAsString(payload));
        assertEquals("P0100", withFault.get("faultCode").asText());
    }

    @Test
    void defaultRouteProviderUsesContractDefaults() {
        SimulatorProperties props = new SimulatorProperties();
        // 契约 §5.2 默认全链路 GCJ-02，初始中心为上海人民广场
        assertEquals(121.473701, props.getBaseCenterLng(), 1e-9);
        assertEquals(31.230416, props.getBaseCenterLat(), 1e-9);

        // 默认 vehicleIds 为空，按 vehicleCount（默认 5）生成 1..5
        RouteProvider provider = new RouteProvider();
        List<VehicleSimState> states = provider.create(props);
        assertEquals(5, states.size());
        assertEquals(1L, states.get(0).getVehicleId());
        assertEquals(5L, states.get(4).getVehicleId());

        // 所有初始点都在 base 框内
        for (VehicleSimState s : states) {
            assertTrue(s.getLng() >= props.getBaseCenterLng() - props.getSpreadLng() - 1e-9);
            assertTrue(s.getLng() <= props.getBaseCenterLng() + props.getSpreadLng() + 1e-9);
            assertTrue(s.getLat() >= props.getBaseCenterLat() - props.getSpreadLat() - 1e-9);
            assertTrue(s.getLat() <= props.getBaseCenterLat() + props.getSpreadLat() + 1e-9);
        }
    }

    @Test
    void explicitVehicleIdsOverrideCount() {
        SimulatorProperties props = new SimulatorProperties();
        props.setVehicleIds(List.of(10L, 20L, 30L));
        props.setVehicleCount(5);

        RouteProvider provider = new RouteProvider();
        List<VehicleSimState> states = provider.create(props);
        assertEquals(3, states.size());
        assertEquals(10L, states.get(0).getVehicleId());
        assertEquals(20L, states.get(1).getVehicleId());
        assertEquals(30L, states.get(2).getVehicleId());
    }

    @Test
    void vehicleSimStateStaysWithinBounds() {
        SimulatorProperties props = new SimulatorProperties();
        VehicleSimState s = new VehicleSimState(
                1L,
                props.getBaseCenterLng(),
                props.getBaseCenterLat(),
                props.getBaseCenterLng(),
                props.getBaseCenterLat(),
                props.getSpreadLng(),
                props.getSpreadLat(),
                props.getMinSpeed(),
                props.getMaxSpeed()
        );
        // 推 100 次（每步 1s 相当于 100s）必然产生足够步进
        for (int i = 0; i < 100; i++) {
            s.step(1000);
            assertTrue(s.getLng() >= props.getBaseCenterLng() - props.getSpreadLng() - 1e-9);
            assertTrue(s.getLng() <= props.getBaseCenterLng() + props.getSpreadLng() + 1e-9);
            assertTrue(s.getLat() >= props.getBaseCenterLat() - props.getSpreadLat() - 1e-9);
            assertTrue(s.getLat() <= props.getBaseCenterLat() + props.getSpreadLat() + 1e-9);
        }
    }
}
