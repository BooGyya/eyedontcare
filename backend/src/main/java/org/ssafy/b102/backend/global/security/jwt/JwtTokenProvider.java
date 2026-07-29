package org.ssafy.b102.backend.global.security.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String TOKEN_TYPE_CLAIM = "tokenType";

    private final JwtProperties properties;
    private final SecretKey secretKey;
    private final Clock clock;

    @Autowired
    public JwtTokenProvider(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    JwtTokenProvider(
        JwtProperties properties,
        Clock clock
    ) {
        this.properties = properties;
        this.secretKey = createSecretKey(properties.secretKey());
        this.clock = clock;
    }

    public TokenPair issueTokenPair(Long userId) {
        return new TokenPair(
            issueAccessToken(userId),
            issueRefreshToken(userId)
        );
    }

    public String issueAccessToken(Long userId) {
        return issueToken(
            userId,
            TokenType.ACCESS,
            properties.accessTokenExpiration()
        );
    }

    public String issueRefreshToken(Long userId) {
        return issueToken(
            userId,
            TokenType.REFRESH,
            properties.refreshTokenExpiration()
        );
    }

    private String issueToken(
        Long userId,
        TokenType tokenType,
        Duration expiration
    ) {
        if (userId == null) {
            throw new IllegalArgumentException(
                "User ID must not be null"
            );
        }

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(expiration);

        return Jwts.builder()
            .subject(userId.toString())
            .claim(TOKEN_TYPE_CLAIM, tokenType.name())
            .issuedAt(Date.from(issuedAt))
            .expiration(Date.from(expiresAt))
            .signWith(secretKey)
            .compact();
    }

    private SecretKey createSecretKey(String encodedSecretKey) {
        byte[] keyBytes = Decoders.BASE64.decode(encodedSecretKey);

        return Keys.hmacShaKeyFor(keyBytes);
    }
}
