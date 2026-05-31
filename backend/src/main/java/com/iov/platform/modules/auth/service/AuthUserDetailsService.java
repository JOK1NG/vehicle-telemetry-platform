package com.iov.platform.modules.auth.service;

import com.iov.platform.modules.auth.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 从数据库加载用户，供 Spring Security 认证使用
 * 使用 JdbcTemplate 直接查询，兼容当前技术栈
 */
@Service
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var users = jdbcTemplate.query(
                "SELECT id, username, password, role, enabled, created_at AS createdAt FROM sys_user WHERE username = ?",
                (rs, rowNum) -> {
                    SysUser u = new SysUser();
                    u.setId(rs.getLong("id"));
                    u.setUsername(rs.getString("username"));
                    u.setPassword(rs.getString("password"));
                    u.setRole(rs.getString("role"));
                    u.setEnabled(rs.getBoolean("enabled"));
                    u.setCreatedAt(rs.getObject("createdAt", java.time.OffsetDateTime.class));
                    return u;
                },
                username
        );
        if (users.isEmpty()) {
            throw new UsernameNotFoundException("用户不存在: " + username);
        }
        return new AuthUserDetails(users.get(0));
    }
}
