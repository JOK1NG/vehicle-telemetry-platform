package com.iov.platform.simulator;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.modules.vehicle.entity.Vehicle;
import com.iov.platform.modules.vehicle.mapper.VehicleMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 本地开发车辆模拟器。
 * 以 vehicle 表为车辆来源，按契约发布到 vehicle/{vehicleId}/telemetry。
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "simulator", name = "enabled", havingValue = "true")
public class VehicleTelemetrySimulator {

    private static final List<double[][]> ROUTES = List.of(
            new double[][]{
                    {121.473701, 31.230416},
                    {121.480734, 31.238841},
                    {121.496032, 31.239017},
                    {121.506377, 31.229714},
                    {121.498889, 31.219920}
            },
            new double[][]{
                    {121.418188, 31.213390},
                    {121.433948, 31.211166},
                    {121.449372, 31.219657},
                    {121.462094, 31.228053},
                    {121.468771, 31.240629}
            },
            new double[][]{
                    {121.524110, 31.222771},
                    {121.532383, 31.231093},
                    {121.544382, 31.239214},
                    {121.557006, 31.234247},
                    {121.548336, 31.222407}
            }
    );

    private final VehicleMapper vehicleMapper;
    private final ObjectMapper objectMapper;
    private final MessageChannel mqttOutboundChannel;
    private final Map<Long, SimulatedVehicleState> states = new ConcurrentHashMap<>();

    @Value("${simulator.vehicle-limit:20}")
    private int vehicleLimit;

    @Value("${simulator.interval-ms:1000}")
    private long intervalMs;

    private Instant lastEmptyLogAt = Instant.EPOCH;

    public VehicleTelemetrySimulator(VehicleMapper vehicleMapper,
                                     ObjectMapper objectMapper,
                                     @Qualifier("mqttOutboundChannel") MessageChannel mqttOutboundChannel) {
        this.vehicleMapper = vehicleMapper;
        this.objectMapper = objectMapper;
        this.mqttOutboundChannel = mqttOutboundChannel;
    }

    @Scheduled(fixedDelayString = "${simulator.interval-ms:1000}")
    public void publishTelemetry() {
        List<Vehicle> vehicles = vehicleMapper.selectList(
                        new LambdaQueryWrapper<Vehicle>().orderByAsc(Vehicle::getId))
                .stream()
                .filter(vehicle -> vehicle.getId() != null)
                .limit(Math.max(vehicleLimit, 0))
                .toList();

        if (vehicles.isEmpty()) {
            logEmptyVehicleHint();
            return;
        }

        Set<Long> activeIds = vehicles.stream().map(Vehicle::getId).collect(Collectors.toSet());
        states.keySet().removeIf(id -> !activeIds.contains(id));

        for (Vehicle vehicle : vehicles) {
            publishVehicle(vehicle);
        }
    }

    @SuppressWarnings("null")
    private void publishVehicle(Vehicle vehicle) {
        SimulatedVehicleState state = states.computeIfAbsent(vehicle.getId(), this::initialState);
        state.advance(intervalMs);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ts", Instant.now().toString());
        payload.put("lng", round(state.lng, 6));
        payload.put("lat", round(state.lat, 6));
        payload.put("speed", round(state.speedKph, 1));
        payload.put("heading", round(state.heading, 1));
        payload.put("battery", round(state.battery, 1));
        payload.put("faultCode", state.faultCode);

        try {
            String topic = "vehicle/" + vehicle.getId() + "/telemetry";
            String body = objectMapper.writeValueAsString(payload);
            mqttOutboundChannel.send(MessageBuilder.withPayload(body)
                    .setHeader(MqttHeaders.TOPIC, topic)
                    .setHeader(MqttHeaders.QOS, 1)
                    .build());
            log.debug("模拟器发布遥测 topic={} payload={}", topic, body);
        } catch (JsonProcessingException e) {
            log.warn("模拟器序列化遥测失败 vehicleId={}: {}", vehicle.getId(), e.getMessage());
        } catch (Exception e) {
            log.warn("模拟器发布 MQTT 失败 vehicleId={}: {}", vehicle.getId(), e.getMessage());
        }
    }

    private SimulatedVehicleState initialState(Long vehicleId) {
        int routeIndex = Math.floorMod(vehicleId.intValue(), ROUTES.size());
        double[][] route = ROUTES.get(routeIndex);
        SimulatedVehicleState state = new SimulatedVehicleState(route);
        state.segmentIndex = Math.floorMod(vehicleId.intValue(), route.length - 1);
        state.progress = ThreadLocalRandom.current().nextDouble(0.0, 0.9);
        state.battery = ThreadLocalRandom.current().nextDouble(68.0, 96.0);
        state.advance(0);
        return state;
    }

    private void logEmptyVehicleHint() {
        Instant now = Instant.now();
        if (now.minusSeconds(60).isAfter(lastEmptyLogAt)) {
            lastEmptyLogAt = now;
            log.info("车辆模拟器已启用，但 vehicle 表暂无车辆。请先通过车辆列表创建车辆。");
        }
    }

    private double round(double value, int digits) {
        double scale = Math.pow(10, digits);
        return Math.round(value * scale) / scale;
    }

    private static final class SimulatedVehicleState {
        private final double[][] route;

        private int segmentIndex;
        private double progress;
        private double lng;
        private double lat;
        private double speedKph;
        private double heading;
        private double battery;
        private String faultCode;

        private SimulatedVehicleState(double[][] route) {
            this.route = route;
        }

        private void advance(long intervalMs) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            speedKph = clamp(speedKph + random.nextDouble(-3.0, 3.0), 28.0, 64.0);
            if (speedKph == 28.0) {
                speedKph = random.nextDouble(32.0, 52.0);
            }

            double[] start = route[segmentIndex];
            double[] end = route[(segmentIndex + 1) % route.length];
            double segmentMeters = Math.max(distanceMeters(start, end), 1.0);
            double intervalSeconds = Math.max(intervalMs, 0) / 1000.0;
            progress += (speedKph * 1000.0 / 3600.0) * intervalSeconds / segmentMeters;

            while (progress >= 1.0) {
                progress -= 1.0;
                segmentIndex = (segmentIndex + 1) % route.length;
                start = route[segmentIndex];
                end = route[(segmentIndex + 1) % route.length];
            }

            lng = interpolate(start[0], end[0], progress);
            lat = interpolate(start[1], end[1], progress);
            heading = heading(start, end);

            battery -= intervalSeconds * random.nextDouble(0.002, 0.006);
            if (battery < 18.0) {
                battery = random.nextDouble(86.0, 98.0);
            }

            faultCode = random.nextDouble() < 0.015 ? "SIM_WARN" : null;
        }

        private static double interpolate(double start, double end, double progress) {
            return start + (end - start) * progress;
        }

        private static double heading(double[] start, double[] end) {
            double degrees = Math.toDegrees(Math.atan2(end[0] - start[0], end[1] - start[1]));
            return degrees < 0 ? degrees + 360.0 : degrees;
        }

        private static double distanceMeters(double[] start, double[] end) {
            double earthRadius = 6_371_000.0;
            double lat1 = Math.toRadians(start[1]);
            double lat2 = Math.toRadians(end[1]);
            double deltaLat = lat2 - lat1;
            double deltaLng = Math.toRadians(end[0] - start[0]);
            double x = deltaLng * Math.cos((lat1 + lat2) / 2.0);
            double y = deltaLat;
            return Math.sqrt(x * x + y * y) * earthRadius;
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}
