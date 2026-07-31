package org.ssafy.b102.backend.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.security.jwt.JwtProperties;

@ExtendWith(MockitoExtension.class)
class RefreshTokenStoreTest {

    private static final String SECRET_KEY =
        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final long ACCESS_TOKEN_EXPIRATION_SECONDS =
        1_800L;

    private static final long REFRESH_TOKEN_EXPIRATION_SECONDS =
        1_209_600L;

    private static final Long USER_ID = 1L;
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String REDIS_KEY =
        "edc:test:auth:refresh-token:1";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RefreshTokenStore refreshTokenStore;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(
            SECRET_KEY,
            ACCESS_TOKEN_EXPIRATION_SECONDS,
            REFRESH_TOKEN_EXPIRATION_SECONDS
        );

        RedisKeyBuilder redisKeyBuilder =
            new RedisKeyBuilder("test");

        refreshTokenStore = new RefreshTokenStore(
            redisTemplate,
            redisKeyBuilder,
            jwtProperties
        );
    }

    @Test
    void 리프레시_토큰을_TTL과_함께_저장한다() {
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        refreshTokenStore.save(USER_ID, REFRESH_TOKEN);

        verify(valueOperations).set(
            REDIS_KEY,
            REFRESH_TOKEN,
            Duration.ofSeconds(
                REFRESH_TOKEN_EXPIRATION_SECONDS
            )
        );
    }

    @Test
    void 사용자_ID로_리프레시_토큰을_조회한다() {
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        when(valueOperations.get(REDIS_KEY))
            .thenReturn(REFRESH_TOKEN);

        Optional<String> result =
            refreshTokenStore.findByUserId(USER_ID);

        assertThat(result).contains(REFRESH_TOKEN);
    }

    @Test
    void 저장된_리프레시_토큰이_없으면_빈_Optional을_반환한다() {
        when(redisTemplate.opsForValue())
            .thenReturn(valueOperations);

        when(valueOperations.get(REDIS_KEY))
            .thenReturn(null);

        Optional<String> result =
            refreshTokenStore.findByUserId(USER_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void 사용자_ID로_리프레시_토큰을_삭제한다() {
        refreshTokenStore.deleteByUserId(USER_ID);

        verify(redisTemplate).delete(REDIS_KEY);
    }

    @Test
    void 사용자_ID가_null이면_저장할_수_없다() {
        assertThatThrownBy(
            () -> refreshTokenStore.save(
                null,
                REFRESH_TOKEN
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("User ID must not be null");

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void 리프레시_토큰이_null이면_저장할_수_없다() {
        assertThatThrownBy(
            () -> refreshTokenStore.save(USER_ID, null)
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Refresh token must not be blank");

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void 빈_리프레시_토큰은_저장할_수_없다() {
        assertThatThrownBy(
            () -> refreshTokenStore.save(USER_ID, " ")
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Refresh token must not be blank");

        verifyNoInteractions(redisTemplate);
    }
}
