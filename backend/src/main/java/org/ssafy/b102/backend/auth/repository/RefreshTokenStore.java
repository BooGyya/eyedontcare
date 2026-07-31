package org.ssafy.b102.backend.auth.repository;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.security.jwt.JwtProperties;

@Component
public class RefreshTokenStore {

    private static final String DOMAIN = "auth";
    private static final String RESOURCE = "refresh-token";

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyBuilder redisKeyBuilder;
    private final Duration refreshTokenExpiration;

    public RefreshTokenStore(
        StringRedisTemplate redisTemplate,
        RedisKeyBuilder redisKeyBuilder,
        JwtProperties jwtProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.redisKeyBuilder = redisKeyBuilder;
        this.refreshTokenExpiration =
            jwtProperties.refreshTokenExpiration();
    }

    public void save(Long userId, String refreshToken) {
        validateUserId(userId);
        validateRefreshToken(refreshToken);

        redisTemplate.opsForValue().set(
            createKey(userId),
            refreshToken,
            refreshTokenExpiration
        );
    }

    public Optional<String> findByUserId(Long userId) {
        validateUserId(userId);

        String refreshToken = redisTemplate.opsForValue()
            .get(createKey(userId));

        return Optional.ofNullable(refreshToken);
    }

    public void deleteByUserId(Long userId) {
        validateUserId(userId);

        redisTemplate.delete(createKey(userId));
    }

    private String createKey(Long userId) {
        return redisKeyBuilder.build(
            DOMAIN,
            RESOURCE,
            userId.toString()
        );
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException(
                "User ID must not be null"
            );
        }
    }

    private void validateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException(
                "Refresh token must not be blank"
            );
        }
    }
}
