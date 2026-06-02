package com.iov.platform.ws;

import com.iov.platform.config.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * STOMP 握手/连接鉴权拦截器
 * 在 CONNECT 帧时校验 JWT token，拒绝未认证的 WebSocket 连接
 *
 * token 来源优先级：
 * 1. STOMP native header "Authorization: Bearer xxx"
 * 2. STOMP native header "token: xxx"
 * 3. SockJS 握手时 URL query 参数 ?token=xxx（通过 simpSessionAttributes 传递）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // 1. 尝试从 STOMP Authorization header 中提取 Bearer token
            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            // 2. 回退：从 native header 中直接取 token
            if (!StringUtils.hasText(token)) {
                token = accessor.getFirstNativeHeader("token");
            }

            // 3. 回退：从 SockJS 握手 URL query 参数中获取 token（通过 simpSessionAttributes 传递）
            if (!StringUtils.hasText(token) && accessor.getSessionAttributes() != null) {
                Object queryToken = accessor.getSessionAttributes().get("token");
                if (queryToken instanceof String queryStr && StringUtils.hasText(queryStr)) {
                    token = queryStr;
                }
            }

            if (!StringUtils.hasText(token)) {
                log.warn("WebSocket CONNECT 拒绝：缺少 token");
                throw new IllegalArgumentException("未提供认证 token，WebSocket 连接被拒绝");
            }

            try {
                if (!jwtUtils.validateToken(token)) {
                    log.warn("WebSocket CONNECT 拒绝：token 无效");
                    throw new IllegalArgumentException("token 无效或已过期，WebSocket 连接被拒绝");
                }

                var claims = jwtUtils.parseToken(token);
                String username = claims.get("username", String.class);
                String role = claims.get("role", String.class);
                String userId = claims.getSubject();

                if (!StringUtils.hasText(username)) {
                    throw new IllegalArgumentException("token 中缺少用户信息");
                }

                // 设置 STOMP session 的用户认证信息
                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + role)
                );
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);
                accessor.setUser(auth);
                log.debug("WebSocket 认证成功: userId={}, role={}", userId, role);
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                log.warn("WebSocket 认证异常: {}", e.getMessage());
                throw new IllegalArgumentException("认证失败，WebSocket 连接被拒绝");
            }
        }

        return message;
    }
}
