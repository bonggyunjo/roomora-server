package com.roomora.server.global.security;

import com.roomora.server.domain.user.User;
import com.roomora.server.domain.user.UserRole;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Getter
public class CustomUserDetails implements UserDetails, OAuth2User {

    private final Long userId;
    private final String email;
    private final String nickname;
    private final UserRole role;
    private final boolean needsOnboarding;
    private final Map<String, Object> attributes;

    public CustomUserDetails(Long userId, String email, String nickname, UserRole role) {
        this(userId, email, nickname, role, false, Map.of());
    }

    public CustomUserDetails(Long userId, String email, String nickname, UserRole role,
                             boolean needsOnboarding, Map<String, Object> attributes) {
        this.userId = userId;
        this.email = email;
        this.nickname = nickname;
        this.role = role;
        this.needsOnboarding = needsOnboarding;
        this.attributes = attributes != null ? attributes : Map.of();
    }

    public static CustomUserDetails from(User user) {
        return new CustomUserDetails(user.getId(), user.getEmail(), user.getNickname(), user.getRole());
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return String.valueOf(userId);
    }
}
