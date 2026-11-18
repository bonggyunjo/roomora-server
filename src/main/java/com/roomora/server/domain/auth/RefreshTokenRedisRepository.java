package com.roomora.server.domain.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository {

    private static final String RT_PREFIX = "auth:rt:";
    private static final String BL_PREFIX = "auth:bl:";

    private final StringRedisTemplate redisTemplate;

    public void saveRefreshToken(Long userId, String refreshToken, Duration ttl) {
        redisTemplate.opsForValue().set(RT_PREFIX + userId, refreshToken, ttl);
    }

    public Optional<String> findRefreshToken(Long userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(RT_PREFIX + userId));
    }

    public void deleteRefreshToken(Long userId) {
        redisTemplate.delete(RT_PREFIX + userId);
    }

    public void blacklistAccessToken(String accessToken, Duration ttl) {
        if (!ttl.isZero() && !ttl.isNegative()) {
            redisTemplate.opsForValue().set(BL_PREFIX + accessToken, "logout", ttl);
        }
    }

    public boolean isBlacklisted(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BL_PREFIX + accessToken));
    }
}
