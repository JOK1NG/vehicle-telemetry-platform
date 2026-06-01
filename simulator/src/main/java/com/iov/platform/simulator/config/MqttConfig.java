package com.iov.platform.simulator.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;

/**
 * MQTT 出站连接 (Paho).
 * 复用与后端一致的客户端实现，仅用作发布者。
 */
@Configuration
@Slf4j
public class MqttConfig {

    private MqttClient client;

    @Bean(destroyMethod = "disconnect")  // Spring 销毁时仅调用 disconnect(),不调用 close()
    public MqttClient mqttClient(SimulatorProperties props) throws Exception {
        SimulatorProperties.Mqtt mqtt = props.getMqtt();
        // Paho clientId 限制 23 字符
        String safeClientId = mqtt.getClientId().length() > 23
                ? mqtt.getClientId().substring(0, 23)
                : mqtt.getClientId();

        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{mqtt.getUrl()});
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setKeepAliveInterval(30);
        options.setConnectionTimeout(10);
        if (mqtt.getUsername() != null && !mqtt.getUsername().isBlank()) {
            options.setUserName(mqtt.getUsername());
        }
        if (mqtt.getPassword() != null && !mqtt.getPassword().isBlank()) {
            options.setPassword(mqtt.getPassword().toCharArray());
        }

        // MemoryPersistence: 模拟器为单进程、启停频繁，内存持久化更轻
        this.client = new MqttClient(mqtt.getUrl(), safeClientId, new MemoryPersistence());
        this.client.connect(options);
        log.info("模拟器 MQTT 已连接 url={} clientId={}", mqtt.getUrl(), safeClientId);
        return this.client;
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            try {
                if (client.isConnected()) {
                    client.disconnect();
                }
                log.info("模拟器 MQTT 连接已断开");
            } catch (Exception e) {
                log.debug("disconnect() 异常（可忽略）: {}", e.getMessage());
            }
        }
    }

    /** 抑制 IDE 静态分析对 StandardCharsets 的未用提示。 */
    @SuppressWarnings("unused")
    private static final java.nio.charset.Charset UTF_8 = StandardCharsets.UTF_8;
}
