package com.iov.platform.modules.auth.controller;

import com.iov.platform.common.Result;
import com.iov.platform.config.JwtUtils;
import com.iov.platform.modules.auth.dto.LoginRequest;
import com.iov.platform.modules.auth.dto.LoginResponse;
import com.iov.platform.modules.auth.dto.UserInfo;
import com.iov.platform.modules.auth.service.AuthUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 认证接口
 *
 * POST /api/auth/login  登录
 * GET  /api/auth/me     获取当前用户信息
 *
 * 安全修复（MUL-39）：登录失败返回 HTTP 401 而非 HTTP 200 + body code 401
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;

    /**
     * 登录
     * 成功：HTTP 200 + token
     * 失败：HTTP 401 + 错误信息（不再返回 HTTP 200 + body code 401）
     */
    @PostMapping("/login")
    public ResponseEntity<Result<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );

            AuthUserDetails userDetails = (AuthUserDetails) auth.getPrincipal();
            var sysUser = userDetails.getSysUser();

            // 签发 JWT
            String token = jwtUtils.generateToken(sysUser.getId(), sysUser.getUsername(), sysUser.getRole());

            UserInfo userInfo = UserInfo.builder()
                    .id(sysUser.getId())
                    .username(sysUser.getUsername())
                    .role(sysUser.getRole())
                    .build();

            return ResponseEntity.ok(Result.ok(LoginResponse.of(token, userInfo)));
        } catch (org.springframework.security.core.AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Result.fail(401, "用户名或密码错误"));
        }
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/me")
    public Result<UserInfo> me(@AuthenticationPrincipal AuthUserDetails userDetails) {
        if (userDetails == null) {
            return Result.fail(401, "未登录");
        }
        var sysUser = userDetails.getSysUser();
        UserInfo userInfo = UserInfo.builder()
                .id(sysUser.getId())
                .username(sysUser.getUsername())
                .role(sysUser.getRole())
                .build();
        return Result.ok(userInfo);
    }
}
