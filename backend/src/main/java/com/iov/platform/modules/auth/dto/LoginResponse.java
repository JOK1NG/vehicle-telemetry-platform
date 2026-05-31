package com.iov.platform.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** JWT token */
    private String token;

    /** Token 类型，固定为 Bearer */
    private String tokenType;

    /** 用户信息 */
    private UserInfo user;

    public static LoginResponse of(String token, UserInfo user) {
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(user)
                .build();
    }
}
