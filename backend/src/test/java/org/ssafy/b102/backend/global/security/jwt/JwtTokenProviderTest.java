package org.ssafy.b102.backend.global.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET_KEY =
        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final Instant FIXED_TIME =
        Instant.parse("2026-07-29T00:00:00Z");

    private static final long ACCESS_TOKEN_EXPIRATION_SECONDS = 1_800L;
    private static final long REFRESH_TOKEN_EXPIRATION_SECONDS = 1_209_600L;

    private JwtTokenProvider jwtTokenProvider;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
            SECRET_KEY,
            ACCESS_TOKEN_EXPIRATION_SECONDS,
            REFRESH_TOKEN_EXPIRATION_SECONDS
        );

        Clock fixedClock = Clock.fixed(
            FIXED_TIME,
            ZoneOffset.UTC
        );

        jwtTokenProvider = new JwtTokenProvider(
            properties,
            fixedClock
        );

        secretKey = Keys.hmacShaKeyFor(
            Decoders.BASE64.decode(SECRET_KEY)
        );
    }

    @Test
    void 액세스_토큰을_발급한다() {
        String token = jwtTokenProvider.issueAccessToken(1L);

        Claims claims = parseClaims(token);

        assertThat(claims.getSubject())
            .isEqualTo("1");

        assertThat(claims.get("tokenType"))
            .isEqualTo(TokenType.ACCESS.name());

        assertThat(claims.getIssuedAt().toInstant())
            .isEqualTo(FIXED_TIME);

        assertThat(claims.getExpiration().toInstant())
            .isEqualTo(
                FIXED_TIME.plusSeconds(
                    ACCESS_TOKEN_EXPIRATION_SECONDS
                )
            );
    }

    @Test
    void 리프레시_토큰을_발급한다() {
        String token = jwtTokenProvider.issueRefreshToken(1L);

        Claims claims = parseClaims(token);

        assertThat(claims.getSubject())
            .isEqualTo("1");

        assertThat(claims.get("tokenType"))
            .isEqualTo(TokenType.REFRESH.name());

        assertThat(claims.getIssuedAt().toInstant())
            .isEqualTo(FIXED_TIME);

        assertThat(claims.getExpiration().toInstant())
            .isEqualTo(
                FIXED_TIME.plusSeconds(
                    REFRESH_TOKEN_EXPIRATION_SECONDS
                )
            );
    }

    @Test
    void 액세스와_리프레시_토큰을_함께_발급한다() {
        TokenPair tokenPair =
            jwtTokenProvider.issueTokenPair(1L);

        Claims accessClaims =
            parseClaims(tokenPair.accessToken());

        Claims refreshClaims =
            parseClaims(tokenPair.refreshToken());

        assertThat(accessClaims.getSubject())
            .isEqualTo("1");

        assertThat(accessClaims.get("tokenType"))
            .isEqualTo(TokenType.ACCESS.name());

        assertThat(refreshClaims.getSubject())
            .isEqualTo("1");

        assertThat(refreshClaims.get("tokenType"))
            .isEqualTo(TokenType.REFRESH.name());
    }

    @Test
    void 사용자_ID가_null이면_토큰을_발급할_수_없다() {
        assertThatThrownBy(
            () -> jwtTokenProvider.issueAccessToken(null)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("User ID must not be null");
    }

    @Test
    void 유효한_액세스_토큰에서_사용자_ID를_추출한다() {
        String token = jwtTokenProvider.issueAccessToken(1L);

        assertThat(
            jwtTokenProvider.parseAccessTokenUserId(token)
        ).contains(1L);
    }

    @Test
    void 만료된_액세스_토큰은_허용하지_않는다() {
        JwtProperties properties = new JwtProperties(
            SECRET_KEY,
            ACCESS_TOKEN_EXPIRATION_SECONDS,
            REFRESH_TOKEN_EXPIRATION_SECONDS
        );

        JwtTokenProvider expiredTokenIssuer =
            new JwtTokenProvider(
                properties,
                Clock.fixed(
                    FIXED_TIME.minusSeconds(3_600L),
                    ZoneOffset.UTC
                )
            );

        String token = expiredTokenIssuer.issueAccessToken(1L);

        assertThat(
            jwtTokenProvider.parseAccessTokenUserId(token)
        ).isEmpty();
    }

    @Test
    void 서명이_다른_액세스_토큰은_허용하지_않는다() {
        String otherSecretKey = Base64.getEncoder()
            .encodeToString(
                "different-secret-key-for-testing"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

        JwtTokenProvider otherTokenProvider =
            new JwtTokenProvider(
                new JwtProperties(
                    otherSecretKey,
                    ACCESS_TOKEN_EXPIRATION_SECONDS,
                    REFRESH_TOKEN_EXPIRATION_SECONDS
                ),
                Clock.fixed(FIXED_TIME, ZoneOffset.UTC)
            );

        String token = otherTokenProvider.issueAccessToken(1L);

        assertThat(
            jwtTokenProvider.parseAccessTokenUserId(token)
        ).isEmpty();
    }

    @Test
    void 리프레시_토큰은_액세스_토큰으로_허용하지_않는다() {
        String token = jwtTokenProvider.issueRefreshToken(1L);

        assertThat(
            jwtTokenProvider.parseAccessTokenUserId(token)
        ).isEmpty();
    }

    @Test
    void 유효한_리프레시_토큰에서_사용자_ID를_추출한다() {
        String token = jwtTokenProvider.issueRefreshToken(1L);

        assertThat(
            jwtTokenProvider.parseRefreshTokenUserId(token)
        ).contains(1L);
    }

    @Test
    void 액세스_토큰은_리프레시_토큰으로_허용하지_않는다() {
        String token = jwtTokenProvider.issueAccessToken(1L);

        assertThat(
            jwtTokenProvider.parseRefreshTokenUserId(token)
        ).isEmpty();
    }

    @Test
    void 만료된_리프레시_토큰은_허용하지_않는다() {
        JwtProperties properties = new JwtProperties(
            SECRET_KEY,
            ACCESS_TOKEN_EXPIRATION_SECONDS,
            REFRESH_TOKEN_EXPIRATION_SECONDS
        );

        JwtTokenProvider expiredTokenIssuer =
            new JwtTokenProvider(
                properties,
                Clock.fixed(
                    FIXED_TIME.minusSeconds(
                        REFRESH_TOKEN_EXPIRATION_SECONDS + 1
                    ),
                    ZoneOffset.UTC
                )
            );

        String token = expiredTokenIssuer.issueRefreshToken(1L);

        assertThat(
            jwtTokenProvider.parseRefreshTokenUserId(token)
        ).isEmpty();
    }

    @Test
    void 변조된_리프레시_토큰은_허용하지_않는다() {
        String token = jwtTokenProvider.issueRefreshToken(1L);
        String tamperedToken = token.substring(
            0,
            token.length() - 1
        ) + (token.endsWith("a") ? "b" : "a");

        assertThat(
            jwtTokenProvider.parseRefreshTokenUserId(
                tamperedToken
            )
        ).isEmpty();
    }

    @Test
    void 리프레시_토큰의_사용자_ID가_Long이_아니면_허용하지_않는다() {
        String token = Jwts.builder()
            .subject("not-a-long")
            .claim("tokenType", TokenType.REFRESH.name())
            .issuedAt(Date.from(FIXED_TIME))
            .expiration(Date.from(FIXED_TIME.plusSeconds(1_800L)))
            .signWith(secretKey)
            .compact();

        assertThat(
            jwtTokenProvider.parseRefreshTokenUserId(token)
        ).isEmpty();
    }

    @Test
    void 리프레시_토큰의_사용자_ID가_0_이하이면_허용하지_않는다() {
        String token = Jwts.builder()
            .subject("0")
            .claim("tokenType", TokenType.REFRESH.name())
            .issuedAt(Date.from(FIXED_TIME))
            .expiration(Date.from(FIXED_TIME.plusSeconds(1_800L)))
            .signWith(secretKey)
            .compact();

        assertThat(
            jwtTokenProvider.parseRefreshTokenUserId(token)
        ).isEmpty();
    }

    @Test
    void 사용자_ID가_Long이_아니면_허용하지_않는다() {
        String token = Jwts.builder()
            .subject("not-a-long")
            .claim("tokenType", TokenType.ACCESS.name())
            .issuedAt(Date.from(FIXED_TIME))
            .expiration(Date.from(FIXED_TIME.plusSeconds(1_800L)))
            .signWith(secretKey)
            .compact();

        assertThat(
            jwtTokenProvider.parseAccessTokenUserId(token)
        ).isEmpty();
    }

    @Test
    void 잘못된_형식의_토큰은_허용하지_않는다() {
        assertThat(
            jwtTokenProvider.parseAccessTokenUserId("not-a-jwt")
        ).isEmpty();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .clock(() -> Date.from(FIXED_TIME))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
