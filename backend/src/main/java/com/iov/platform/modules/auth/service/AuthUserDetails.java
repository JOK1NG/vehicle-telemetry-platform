package com.iov.platform.modules.auth.service;

import com.iov.platform.modules.auth.entity.SysUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Spring Security UserDetails 实现
 * 将 SysUser 适配为 Security 可识别的用户对象
 */
public class AuthUserDetails implements UserDetails {

    private final SysUser sysUser;

    public AuthUserDetails(SysUser sysUser) {
        this.sysUser = sysUser;
    }

    public SysUser getSysUser() {
        return sysUser;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // ROLE_ 前缀是 Spring Security hasRole 的约定
        return List.of(new SimpleGrantedAuthority("ROLE_" + sysUser.getRole()));
    }

    @Override
    public String getPassword() {
        return sysUser.getPassword();
    }

    @Override
    public String getUsername() {
        return sysUser.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return sysUser.getEnabled() != null && sysUser.getEnabled();
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }
}
