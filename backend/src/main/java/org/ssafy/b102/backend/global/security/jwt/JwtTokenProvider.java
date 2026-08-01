package org.ssafy.b102.backend.global.security.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
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

    public Optional<Long> parseAccessTokenUserId(String token) {
        return parseTokenUserId(token, TokenType.ACCESS);
    }

    public Optional<Long> parseRefreshTokenUserId(String token) {
        return parseTokenUserId(token, TokenType.REFRESH);
    }

    private Optional<Long> parseTokenUserId(
        String token,
        TokenType expectedTokenType
    ) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            var claims = Jwts.parser()
                .verifyWith(secretKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();

            String tokenType = claims.get(
                TOKEN_TYPE_CLAIM,
                String.class
            );

            if (!expectedTokenType.name().equals(tokenType)) {
                return Optional.empty();
            }

            Long userId = Long.valueOf(claims.getSubject());
            return userId > 0
                ? Optional.of(userId)
                : Optional.empty();
        } catch (
            JwtException |
            IllegalArgumentException exception
        ) {
            return Optional.empty();
        }
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
