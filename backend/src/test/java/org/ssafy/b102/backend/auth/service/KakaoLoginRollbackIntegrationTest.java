package org.ssafy.b102.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.ssafy.b102.backend.auth.dto.request.KakaoLoginRequest;
import org.ssafy.b102.backend.auth.kakao.KakaoOAuthClient;
import org.ssafy.b102.backend.auth.kakao.KakaoUserIdentity;
import org.ssafy.b102.backend.auth.repository.RefreshTokenStore;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.global.security.jwt.TokenPair;
import org.ssafy.b102.backend.user.enums.SocialProvider;
import org.ssafy.b102.backend.user.repository.SocialAccountRepository;
import org.ssafy.b102.backend.user.repository.UserRepository;
import org.ssafy.b102.backend.user.util.RandomNicknameGenerator;

@SpringBootTest
class KakaoLoginRollbackIntegrationTest {

    private static final String PROVIDER_USER_ID =
        "rollback-test-provider-user";
    private static final String NICKNAME =
        "rollback-test-nickname";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SocialAccountRepository socialAccountRepository;

    @MockitoBean
    private KakaoOAuthClient kakaoOAuthClient;

    @MockitoBean
    private RandomNicknameGenerator randomNicknameGenerator;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private RefreshTokenStore refreshTokenStore;

    @Test
    void Redis_저장_실패시_User와_SocialAccount가_함께_rollback된다() {
        when(
            kakaoOAuthClient.authenticate("authorization-code")
        ).thenReturn(
            new KakaoUserIdentity(PROVIDER_USER_ID)
        );
        when(randomNicknameGenerator.generate())
            .thenReturn(NICKNAME);
        when(jwtTokenProvider.issueTokenPair(any()))
            .thenReturn(
                new TokenPair(
                    "access-token",
                    "refresh-token"
                )
            );
        doThrow(new RuntimeException("redis unavailable"))
            .when(refreshTokenStore)
            .save(any(), any());

        assertThatThrownBy(
            () -> authService.loginWithKakao(
                new KakaoLoginRequest("authorization-code")
            )
        ).isInstanceOf(RuntimeException.class);

        assertThat(
            socialAccountRepository.findActiveAccount(
                SocialProvider.KAKAO,
                PROVIDER_USER_ID
            )
        ).isEmpty();
        assertThat(
            userRepository.findAll().stream()
                .noneMatch(user ->
                    NICKNAME.equals(user.getNickname())
                )
        ).isTrue();
    }
}
