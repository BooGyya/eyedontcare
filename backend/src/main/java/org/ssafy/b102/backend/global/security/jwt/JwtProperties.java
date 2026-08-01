package org.ssafy.b102.backend.global.security.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String secretKey,
    long accessTokenExpirationSeconds,
    long refreshTokenExpirationSeconds
) {

    public JwtProperties {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException(
                "JWT secret key must not be blank"
            );
        }

        if (accessTokenExpirationSeconds <= 0) {
            throw new IllegalArgumentException(
                "Access token expiration must be positive"
            );
        }

        if (refreshTokenExpirationSeconds <= 0) {
            throw new IllegalArgumentException(
                "Refresh token expiration must be positive"
            );
        }
    }

    public Duration accessTokenExpiration() {
        return Duration.ofSeconds(accessTokenExpirationSeconds);
    }

    public Duration refreshTokenExpiration() {
        return Duration.ofSeconds(refreshTokenExpirationSeconds);
    }
}
