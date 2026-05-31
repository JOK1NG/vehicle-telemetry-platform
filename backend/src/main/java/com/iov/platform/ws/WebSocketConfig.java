package com.iov.platform.ws;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * WebSocket / STOMP 配置骨架 (M0)
 * 后续 M2 实现：StompEndpointRegistry + SimpMessagingTemplate 广播 /topic/vehicles
 *
 * M0 不启用实际 WebSocket 端点。
 */
// @Configuration
// @EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig /* implements WebSocketMessageBrokerConfigurer */ {

    // TODO: registerStompEndpoints, configureMessageBroker, inbound channel interceptors
}
