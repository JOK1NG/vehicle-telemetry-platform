package com.iov.platform.simulator.runner;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iov.platform.simulator.config.SimulatorProperties;
import com.iov.platform.simulator.model.TelemetryPayload;
import com.iov.platform.simulator.model.VehicleSimState;
import com.iov.platform.simulator.route.RouteProvider;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 模拟器主循环。
 *
 * - 启动后按 simulator.autoStart 决定是否自动开始。
 * - 每 publish-interval-ms 推进一次所有车辆状态，并发布到 vehicle/{id}/telemetry，QoS 1。
 * - 故障码按 simulator.fault-probability 随机注入；用于联调告警引擎。
 */
@Component
@Slf4j
public class SimulatorRunner {

    /** topic 模板：与契约 §2.1 严格一致。 */
    public static final String TOPIC_TEMPLATE = "vehicle/%d/telemetry";

    private static final int MQTT_QOS = 1;

    private final SimulatorProperties props;
    private final RouteProvider routeProvider;
    private final MqttClient mqttClient;
    private final ObjectMapper objectMapper;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private List<VehicleSimState> states;
    private ScheduledExecutorService scheduler;
    private long lastStepMs;

    @Autowired
    public SimulatorRunner(SimulatorProperties props,
                           RouteProvider routeProvider,
                           MqttClient mqttClient,
                           ObjectMapper objectMapper) {
        this.props = props;
        this.routeProvider = routeProvider;
        this.mqttClient = mqttClient;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        this.states = routeProvider.create(props);
        log.info("模拟器就绪，车辆数={} vehicleIds={} baseCenter=({:.6f}, {:.6f}) spread=({:.4f}, {:.4f})",
                states.size(), idsAsString(), props.getBaseCenterLng(), props.getBaseCenterLat(),
                props.getSpreadLng(), props.getSpreadLat());
        if (props.isAutoStart()) {
            start();
        } else {
            log.info("simulator.autoStart=false，等待外部触发 start()");
        }
    }

    /** 启动发布循环。 */
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            this.lastStepMs = System.currentTimeMillis();
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "simulator-publish");
                t.setDaemon(true);
                return t;
            });
            long interval = props.getPublishIntervalMs();
            scheduler.scheduleAtFixedRate(this::tick, interval, interval, TimeUnit.MILLISECONDS);
            log.info("模拟器已启动，publishInterval={}ms", interval);
        }
    }

    /** 停止发布循环。 */
    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            if (scheduler != null) {
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            log.info("模拟器已停止");
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    @PreDestroy
    public void shutdown() {
        stop();
    }

    /** 单次推进 + 批量发布。 */
    void tick() {
        if (!running.get() || states == null) return;
        long now = System.currentTimeMillis();
        long dt = now - lastStepMs;
        lastStepMs = now;

        for (VehicleSimState state : states) {
            state.step(dt);
            try {
                publishOne(state, now);
            } catch (Exception e) {
                log.error("发布 telemetry 失败 vehicleId={}: {}", state.getVehicleId(), e.getMessage());
            }
        }
    }

    private void publishOne(VehicleSimState state, long nowMs) throws JsonProcessingException, MqttException {
        TelemetryPayload payload = new TelemetryPayload(
                Instant.ofEpochMilli(nowMs).toString(),
                round6(state.getLng()),
                round6(state.getLat()),
                round2(state.getSpeed()),
                round2(state.getHeading()),
                round2(state.getBattery()),
                maybeFault()
        );

        byte[] body = objectMapper.writeValueAsBytes(payload);
        String topic = String.format(TOPIC_TEMPLATE, state.getVehicleId());

        MqttMessage message = new MqttMessage(body);
        message.setQos(MQTT_QOS);
        mqttClient.publish(topic, message);

        if (log.isDebugEnabled()) {
            log.debug("publish topic={} payload={}", topic, new String(body, StandardCharsets.UTF_8));
        }
    }

    private String maybeFault() {
        double p = props.getFaultProbability();
        if (p <= 0.0) return null;
        if (ThreadLocalRandom.current().nextDouble() < p) {
            // 故障码集合保持简短，足够联调使用
            String[] codes = {"P0100", "BATT_LOW", "GPS_LOST"};
            return codes[ThreadLocalRandom.current().nextInt(codes.length)];
        }
        return null;
    }

    private String idsAsString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < states.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(states.get(i).getVehicleId());
        }
        return sb.append("]").toString();
    }

    private static double round6(double v) {
        return Math.round(v * 1_000_000d) / 1_000_000d;
    }

    private static double round2(double v) {
        return Math.round(v * 100d) / 100d;
    }
}
