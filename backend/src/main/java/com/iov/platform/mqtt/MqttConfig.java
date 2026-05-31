package com.iov.platform.mqtt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessagingException;

/**
 * MQTT 配置骨架 (M0)
 * 后续 M2 实现：Inbound/Outbound Channel Adapter + 消息分发到 telemetry 处理
 *
 * 注意：M0 阶段不实际订阅/连接，避免启动时依赖 EMQX。
 * 激活时取消 @Configuration 上的注释或条件装配。
 */
// @Configuration
@Slf4j
public class MqttConfig {

    // TODO: @Bean MqttPahoClientFactory, MessageChannels, @ServiceActivator inbound

    // 占位处理器示例
    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public MessageHandler mqttInboundHandler() {
        return new MessageHandler() {
            @Override
            public void handleMessage(Message<?> message) throws MessagingException {
                log.info("[M0 stub] received mqtt message: {}", message.getPayload());
            }
        };
    }
}
