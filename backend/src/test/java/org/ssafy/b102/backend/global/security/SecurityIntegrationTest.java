package org.ssafy.b102.backend.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.ssafy.b102.backend.auth.controller.AuthController;
import org.ssafy.b102.backend.auth.dto.request.LoginRequest;
import org.ssafy.b102.backend.auth.dto.request.ReissueRequest;
import org.ssafy.b102.backend.auth.dto.request.SignupRequest;
import org.ssafy.b102.backend.auth.dto.response.TokenResponse;
import org.ssafy.b102.backend.auth.service.AuthService;
import org.ssafy.b102.backend.game.controller.GameController;
import org.ssafy.b102.backend.game.service.GameService;
import org.ssafy.b102.backend.gameresult.controller.GameResultController;
import org.ssafy.b102.backend.gameresult.controller.GameResultQueryController;
import org.ssafy.b102.backend.gameresult.dto.response.GameResultDetailResponse;
import org.ssafy.b102.backend.gameresult.dto.response.MyGameResultPageResponse;
import org.ssafy.b102.backend.gameresult.dto.response.SubmitGameResultResponse;
import org.ssafy.b102.backend.gameresult.exception.GameResultErrorCode;
import org.ssafy.b102.backend.gameresult.service.GameResultQueryService;
import org.ssafy.b102.backend.gameresult.service.GameResultService;
import org.ssafy.b102.backend.global.config.CorsConfig;
import org.ssafy.b102.backend.global.config.SecurityConfig;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.security.jwt.JwtProperties;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.global.security.jwt.TokenType;
import org.ssafy.b102.backend.matchmaking.controller.MatchmakingController;
import org.ssafy.b102.backend.matchmaking.service.MatchmakingService;
import org.ssafy.b102.backend.matchmaking.support.MatchParticipantResolver;
import org.ssafy.b102.backend.matchmaking.support.ResolvedParticipant;
import org.ssafy.b102.backend.user.controller.UserController;
import org.ssafy.b102.backend.user.dto.request.UserUpdateRequest;
import org.ssafy.b102.backend.user.dto.request.PasswordUpdateRequest;
import org.ssafy.b102.backend.user.dto.response.NicknameCheckResponse;
import org.ssafy.b102.backend.user.dto.response.UserResponse;
import org.ssafy.b102.backend.user.enums.ProfileImageCode;
import org.ssafy.b102.backend.user.enums.UserLoginType;
import org.ssafy.b102.backend.user.exception.UserErrorCode;
import org.ssafy.b102.backend.user.repository.UserRepository;
import org.ssafy.b102.backend.user.service.UserService;

@WebMvcTest(controllers = {
    AuthController.class,
    GameController.class,
    GameResultController.class,
    GameResultQueryController.class,
    MatchmakingController.class,
    UserController.class
})
@Import({
    SecurityConfig.class,
    CorsConfig.class,
    SecurityAuthenticationEntryPoint.class,
    SecurityAccessDeniedHandler.class,
    SecurityErrorResponseWriter.class,
    JwtTokenProvider.class
})
@EnableConfigurationProperties(JwtProperties.class)
@ImportAutoConfiguration(
    ServletWebSecurityAutoConfiguration.class
)
@TestPropertySource(properties = {
    "app.cors.allowed-origins=http://localhost:5173",
    "jwt.secret-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "jwt.access-token-expiration-seconds=1800",
    "jwt.refresh-token-expiration-seconds=1209600"
})
class SecurityIntegrationTest {

    private static final String SECRET_KEY =
        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private static final String GAME_RESULT_BODY = """
        {
          "playId": "019abcde-1234-4abc-8def-0123456789ab",
          "gameId": 1,
          "startedAt": "2026-07-29T09:00:00Z",
          "endedAt": "2026-07-29T09:01:00Z",
          "participants": [
            {
              "participantKey": "GUEST:abc",
              "participantType": "GUEST",
              "slotNo": 1,
              "displayName": "게스트",
              "outcome": "WIN",
              "rank": 1
            }
          ],
          "gameResult": {
            "durationMs": 60000
          }
        }
        """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private GameResultService gameResultService;

    @MockitoBean
    private GameResultQueryService gameResultQueryService;

    @MockitoBean
    private GameService gameService;

    @MockitoBean
    private MatchmakingService matchmakingService;

    @MockitoBean
    private MatchParticipantResolver matchParticipantResolver;

    @MockitoBean
    private UserService userService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 회원가입_API는_토큰_없이_접근할_수_있다()
        throws Exception {

        when(authService.signup(any(SignupRequest.class)))
            .thenReturn(new TokenResponse("access", "refresh"));

        mockMvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "user@example.com",
                          "password": "password123"
                        }
                        """)
            )
            .andExpect(status().isCreated());
    }

    @Test
    void 로그인_API는_토큰_없이_접근할_수_있다()
        throws Exception {

        when(authService.login(any(LoginRequest.class)))
            .thenReturn(new TokenResponse("access", "refresh"));

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "user@example.com",
                          "password": "password123"
                        }
                        """)
            )
            .andExpect(status().isOk());
    }

    @Test
    void 카카오_로그인_API는_토큰_없이_접근할_수_있다()
        throws Exception {

        when(
            authService.loginWithKakao(any())
        ).thenReturn(
            new TokenResponse(
                "access-token",
                "refresh-token"
            )
        );

        mockMvc.perform(
                post("/api/v1/auth/login/kakao")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "authorizationCode": "authorization-code"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("AUTH_KAKAO_LOGIN_SUCCESS")
            );
    }

    @Test
    void 토큰_재발급_API는_Authorization_헤더_없이_접근할_수_있다()
        throws Exception {

        when(
            authService.reissue(any(ReissueRequest.class))
        ).thenReturn(
            new TokenResponse(
                "new-access-token",
                "new-refresh-token"
            )
        );

        mockMvc.perform(
                post("/api/v1/auth/reissue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "refreshToken": "refresh-token"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.data.accessToken")
                    .value("new-access-token")
            )
            .andExpect(
                jsonPath("$.data.refreshToken")
                    .value("new-refresh-token")
            );

        verify(authService).reissue(
            new ReissueRequest("refresh-token")
        );
    }

    @Test
    void 유효한_액세스_토큰으로_로그아웃하고_사용자_헤더는_무시한다()
        throws Exception {

        String token = activeUserToken(1L);

        mockMvc.perform(
                post("/api/v1/auth/logout")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
                    .header(
                        "X-Participant-Key",
                        "USER:999"
                    )
                    .header("X-User-Id", "999")
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("AUTH_LOGOUT_SUCCESS")
            )
            .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService).logout(1L);
    }

    @Test
    void 로그아웃은_반복해서_호출해도_성공한다()
        throws Exception {

        String token = activeUserToken(1L);

        for (int request = 0; request < 2; request++) {
            mockMvc.perform(
                    post("/api/v1/auth/logout")
                        .header(
                            HttpHeaders.AUTHORIZATION,
                            bearer(token)
                        )
                )
                .andExpect(status().isOk());
        }

        verify(authService, times(2)).logout(1L);
    }

    @Test
    void 로그아웃은_액세스_토큰이_없으면_401을_반환한다()
        throws Exception {

        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SECURITY-001"));
    }

    @Test
    void 만료_변조_리프레시_토큰으로_로그아웃하면_401을_반환한다()
        throws Exception {

        String expiredToken = signedToken(
            1L,
            TokenType.ACCESS,
            Instant.now().minusSeconds(60L),
            secretKey()
        );

        SecretKey otherKey = Keys.hmacShaKeyFor(
            "different-secret-key-for-testing"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        String tamperedToken = signedToken(
            1L,
            TokenType.ACCESS,
            Instant.now().plusSeconds(1_800L),
            otherKey
        );

        String refreshToken =
            jwtTokenProvider.issueRefreshToken(1L);

        expectInvalidLogoutToken(expiredToken);
        expectInvalidLogoutToken(tamperedToken);
        expectInvalidLogoutToken(refreshToken);
    }

    @Test
    void 로그아웃한_액세스_토큰은_만료_전까지_보호_API에_사용할_수_있다()
        throws Exception {

        String token = activeUserToken(1L);

        mockMvc.perform(
                post("/api/v1/auth/logout")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isOk());

        when(
            gameResultQueryService.getMyResults(
                1L,
                1,
                10
            )
        ).thenReturn(
            new MyGameResultPageResponse(
                List.of(),
                1,
                10,
                0
            )
        );

        mockMvc.perform(
                get("/api/v1/game-results/me")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isOk());
    }

    @Test
    void 유효한_액세스_토큰으로_회원_탈퇴하고_사용자_헤더는_무시한다()
        throws Exception {

        String token = activeUserToken(1L);

        mockMvc.perform(
                delete("/api/v1/auth/withdraw")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
                    .header(
                        "X-Participant-Key",
                        "USER:999"
                    )
                    .header("X-User-Id", "999")
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("AUTH_WITHDRAWAL_SUCCESS")
            )
            .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService).withdraw(1L);
    }

    @Test
    void 탈퇴_후_같은_액세스_토큰과_반복_탈퇴는_401이다()
        throws Exception {

        String token = jwtTokenProvider.issueAccessToken(1L);
        when(
            userRepository.existsByIdAndDeletedAtIsNull(1L)
        ).thenReturn(true, false, false);

        mockMvc.perform(
                delete("/api/v1/auth/withdraw")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isOk());

        mockMvc.perform(
                get("/api/v1/game-results/me")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SECURITY-002"));

        mockMvc.perform(
                delete("/api/v1/auth/withdraw")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SECURITY-002"));

        verify(authService).withdraw(1L);
    }

    @Test
    void 탈퇴_API는_토큰이_없으면_401을_반환한다()
        throws Exception {

        mockMvc.perform(delete("/api/v1/auth/withdraw"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SECURITY-001"));
    }

    @Test
    void 만료_변조_리프레시_토큰으로_탈퇴하면_401을_반환한다()
        throws Exception {

        String expiredToken = signedToken(
            1L,
            TokenType.ACCESS,
            Instant.now().minusSeconds(60L),
            secretKey()
        );

        SecretKey otherKey = Keys.hmacShaKeyFor(
            "different-secret-key-for-testing"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        String tamperedToken = signedToken(
            1L,
            TokenType.ACCESS,
            Instant.now().plusSeconds(1_800L),
            otherKey
        );

        String refreshToken =
            jwtTokenProvider.issueRefreshToken(1L);

        expectInvalidWithdrawalToken(expiredToken);
        expectInvalidWithdrawalToken(tamperedToken);
        expectInvalidWithdrawalToken(refreshToken);
    }

    @Test
    void 탈퇴_후_기존_리프레시_토큰과_로그인은_거부된다()
        throws Exception {

        String token = activeUserToken(1L);

        mockMvc.perform(
                delete("/api/v1/auth/withdraw")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isOk());

        when(
            authService.reissue(any(ReissueRequest.class))
        ).thenThrow(
            new BusinessException(
                org.ssafy.b102.backend.auth.exception
                    .AuthErrorCode.INVALID_REFRESH_TOKEN
            )
        );

        mockMvc.perform(
                post("/api/v1/auth/reissue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "refreshToken": "old-refresh-token"
                        }
                        """
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-004"));

        when(
            authService.login(any(LoginRequest.class))
        ).thenThrow(
            new BusinessException(
                org.ssafy.b102.backend.auth.exception
                    .AuthErrorCode.INVALID_CREDENTIALS
            )
        );

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "user@example.com",
                          "password": "password123"
                        }
                        """
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-003"));
    }

    @Test
    void 게임_목록과_상세는_토큰_없이_접근할_수_있다()
        throws Exception {

        mockMvc.perform(get("/api/v1/games"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/games/{gameId}", 1L))
            .andExpect(status().isOk());
    }

    @Test
    void 랜덤_매칭_요청과_취소는_토큰_없이_접근할_수_있다()
        throws Exception {

        when(matchParticipantResolver.resolveForJoin(any(), any()))
            .thenReturn(ResolvedParticipant.member("USER:1"));
        when(matchParticipantResolver.resolveExistingKey(any(), any()))
            .thenReturn("USER:1");

        mockMvc.perform(
                post("/api/v1/match/join")
                    .header(
                        "X-Participant-Key",
                        "GUEST:abc"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "gameType": "HOCKEY"
                        }
                        """)
            )
            .andExpect(status().isOk());

        mockMvc.perform(
                delete("/api/v1/match/cancel")
                    .header(
                        "X-Participant-Key",
                        "GUEST:abc"
                    )
            )
            .andExpect(status().isOk());
    }

    @Test
    void 보호_API는_토큰이_없으면_ApiResponse_401을_반환한다()
        throws Exception {

        mockMvc.perform(get("/api/v1/game-results/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SECURITY-001"))
            .andExpect(jsonPath("$.message").value("인증이 필요합니다."))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 유효한_액세스_토큰이면_보호_API에_접근할_수_있다()
        throws Exception {

        String token = activeUserToken(1L);

        when(
            gameResultQueryService.getMyResults(
                1L,
                1,
                10
            )
        ).thenReturn(
            new MyGameResultPageResponse(
                List.of(),
                1,
                10,
                0
            )
        );

        mockMvc.perform(
                get("/api/v1/game-results/me")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("RESULT_LIST_FOUND"));
    }

    @Test
    void 만료된_액세스_토큰이면_401을_반환한다()
        throws Exception {

        String token = signedToken(
            1L,
            TokenType.ACCESS,
            Instant.now().minusSeconds(60L),
            secretKey()
        );

        expectInvalidToken(token);
    }

    @Test
    void 서명이_잘못된_토큰이면_401을_반환한다()
        throws Exception {

        SecretKey otherKey = Keys.hmacShaKeyFor(
            "different-secret-key-for-testing"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        String token = signedToken(
            1L,
            TokenType.ACCESS,
            Instant.now().plusSeconds(1_800L),
            otherKey
        );

        expectInvalidToken(token);
    }

    @Test
    void 리프레시_토큰으로_보호_API에_접근하면_401을_반환한다()
        throws Exception {

        String token = jwtTokenProvider.issueRefreshToken(1L);
        expectInvalidToken(token);
    }

    @Test
    void Bearer_형식이_잘못되면_401을_반환한다()
        throws Exception {

        mockMvc.perform(
                get("/api/v1/game-results/me")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        "Token invalid"
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SECURITY-002"));
    }

    @Test
    void 존재하지_않는_사용자의_토큰이면_401을_반환한다()
        throws Exception {

        String token = jwtTokenProvider.issueAccessToken(99L);
        when(
            userRepository.existsByIdAndDeletedAtIsNull(99L)
        ).thenReturn(false);

        expectInvalidToken(token);
    }

    @Test
    void 탈퇴한_사용자의_토큰이면_401을_반환한다()
        throws Exception {

        String token = jwtTokenProvider.issueAccessToken(2L);
        when(
            userRepository.existsByIdAndDeletedAtIsNull(2L)
        ).thenReturn(false);

        expectInvalidToken(token);
    }

    @Test
    void 인증된_사용자는_자신의_결과_목록을_조회한다()
        throws Exception {

        String token = activeUserToken(1L);

        when(
            gameResultQueryService.getMyResults(
                1L,
                1,
                10
            )
        ).thenReturn(
            new MyGameResultPageResponse(
                List.of(),
                1,
                10,
                0
            )
        );

        mockMvc.perform(
                get("/api/v1/game-results/me")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isOk());

        verify(gameResultQueryService)
            .getMyResults(1L, 1, 10);
    }

    @Test
    void 참가자_헤더보다_JWT_사용자_ID를_사용한다()
        throws Exception {

        String token = activeUserToken(1L);

        when(
            gameResultQueryService.getMyResults(
                anyLong(),
                anyInt(),
                anyInt()
            )
        ).thenReturn(
            new MyGameResultPageResponse(
                List.of(),
                1,
                10,
                0
            )
        );

        mockMvc.perform(
                get("/api/v1/game-results/me")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
                    .header(
                        "X-Participant-Key",
                        "USER:999"
                    )
            )
            .andExpect(status().isOk());

        verify(gameResultQueryService)
            .getMyResults(1L, 1, 10);
    }

    @Test
    void 인증된_사용자가_참여한_결과_상세를_조회한다()
        throws Exception {

        String token = activeUserToken(1L);

        mockMvc.perform(
                get("/api/v1/game-results/{resultId}", 100L)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isOk());

        verify(gameResultQueryService)
            .getResult(1L, 100L);
    }

    @Test
    void 다른_사용자의_결과_상세_접근은_거부된다()
        throws Exception {

        String token = activeUserToken(2L);

        when(gameResultQueryService.getResult(2L, 100L))
            .thenThrow(
                new BusinessException(
                    GameResultErrorCode.RESULT_ACCESS_DENIED
                )
            );

        mockMvc.perform(
                get("/api/v1/game-results/{resultId}", 100L)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("GAMERESULT-008"));
    }

    @Test
    void 게스트_게임_결과_제출은_토큰_없이_동작한다()
        throws Exception {

        when(
            gameResultService.submit(
                anyString(),
                any()
            )
        ).thenReturn(new SubmitGameResultResponse(1L));

        mockMvc.perform(
                post("/api/v1/game-results")
                    .header(
                        "X-Guest-Session-Id",
                        "019abcde-5678-4abc-8def-0123456789ab"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(GAME_RESULT_BODY)
            )
            .andExpect(status().isCreated());
    }

    @Test
    void 유효한_액세스_토큰으로_내_정보를_조회하고_참가자_헤더는_무시한다()
        throws Exception {

        String token = activeUserToken(1L);
        when(userService.getMyInfo(1L, 1L))
            .thenReturn(new UserResponse(
                1L,
                "user@example.com",
                "용감한수달0123",
                ProfileImageCode.PROFILE_1,
                UserLoginType.LOCAL,
                Instant.parse("2026-07-30T00:00:00Z")
            ));

        mockMvc.perform(
                get("/api/v1/users/{userId}", 1L)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
                    .header(
                        "X-Participant-Key",
                        "USER:999"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("USER_READ_SUCCESS")
            )
            .andExpect(jsonPath("$.data.id").value(1L));

        verify(userService).getMyInfo(1L, 1L);
    }

    @Test
    void 내_정보_조회는_토큰이_없으면_401이다()
        throws Exception {

        mockMvc.perform(
                get("/api/v1/users/{userId}", 1L)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code").value("SECURITY-001")
            );
    }

    @Test
    void 만료_변조_리프레시_토큰으로_내_정보를_조회할_수_없다()
        throws Exception {

        String expiredToken = signedToken(
            1L,
            TokenType.ACCESS,
            Instant.now().minusSeconds(1L),
            secretKey()
        );
        String tamperedToken = signedToken(
            1L,
            TokenType.ACCESS,
            Instant.now().plusSeconds(300L),
            Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(
                    "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
                )
            )
        );
        String refreshToken =
            jwtTokenProvider.issueRefreshToken(1L);

        expectInvalidUserReadToken(expiredToken);
        expectInvalidUserReadToken(tamperedToken);
        expectInvalidUserReadToken(refreshToken);
    }

    @Test
    void 탈퇴한_회원의_액세스_토큰으로_내_정보를_조회할_수_없다()
        throws Exception {

        String token = jwtTokenProvider.issueAccessToken(1L);
        when(
            userRepository.existsByIdAndDeletedAtIsNull(1L)
        ).thenReturn(false);

        expectInvalidUserReadToken(token);
    }

    @Test
    void 인증된_회원이_다른_userId를_조회하면_403이다()
        throws Exception {

        String token = activeUserToken(1L);
        when(userService.getMyInfo(2L, 1L))
            .thenThrow(new BusinessException(
                UserErrorCode.USER_ACCESS_DENIED
            ));

        mockMvc.perform(
                get("/api/v1/users/{userId}", 2L)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.code").value("USER-002")
            );

        verify(userService).getMyInfo(2L, 1L);
    }

    @Test
    void 유효한_액세스_토큰으로_내_정보를_수정하고_참가자_헤더는_무시한다()
        throws Exception {

        String token = activeUserToken(1L);
        when(
            userService.updateMyInfo(
                eq(1L),
                eq(1L),
                any(UserUpdateRequest.class)
            )
        ).thenReturn(new UserResponse(
            1L,
            "user@example.com",
            "새닉네임",
            ProfileImageCode.PROFILE_2,
            UserLoginType.LOCAL,
            Instant.parse("2026-07-30T00:00:00Z")
        ));

        mockMvc.perform(
                patch("/api/v1/users/{userId}", 1L)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
                    .header(
                        "X-Participant-Key",
                        "USER:999"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "nickname": "새닉네임",
                          "profileImageCode": "PROFILE_2"
                        }
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("USER_UPDATE_SUCCESS")
            );

        verify(userService).updateMyInfo(
            eq(1L),
            eq(1L),
            any(UserUpdateRequest.class)
        );
    }

    @Test
    void 내_정보_수정은_토큰이_없으면_401이다()
        throws Exception {

        mockMvc.perform(
                patch("/api/v1/users/{userId}", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"nickname\":\"새닉네임\"}")
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code").value("SECURITY-001")
            );
    }

    @Test
    void 유효한_액세스_토큰으로_비밀번호를_변경하고_참가자_헤더는_무시한다()
        throws Exception {

        String token = activeUserToken(1L);

        mockMvc.perform(
                put("/api/v1/users/{userId}/password", 1L)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
                    .header(
                        "X-Participant-Key",
                        "USER:999"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(passwordUpdateBody())
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("PASSWORD_UPDATE_SUCCESS")
            );

        verify(userService).updatePassword(
            eq(1L),
            eq(1L),
            any(PasswordUpdateRequest.class)
        );
    }

    @Test
    void 비밀번호_변경은_토큰이_없으면_401이다()
        throws Exception {

        mockMvc.perform(
                put("/api/v1/users/{userId}/password", 1L)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(passwordUpdateBody())
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code").value("SECURITY-001")
            );
    }

    @Test
    void 만료_변조_리프레시_토큰으로_비밀번호를_변경할_수_없다()
        throws Exception {

        String expiredToken = signedToken(
            1L,
            TokenType.ACCESS,
            Instant.now().minusSeconds(1L),
            secretKey()
        );
        String tamperedToken = signedToken(
            1L,
            TokenType.ACCESS,
            Instant.now().plusSeconds(300L),
            Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(
                    "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
                )
            )
        );
        String refreshToken =
            jwtTokenProvider.issueRefreshToken(1L);

        expectInvalidPasswordUpdateToken(expiredToken);
        expectInvalidPasswordUpdateToken(tamperedToken);
        expectInvalidPasswordUpdateToken(refreshToken);
    }

    @Test
    void 탈퇴_회원과_다른_userId는_비밀번호를_변경할_수_없다()
        throws Exception {

        String withdrawnToken =
            jwtTokenProvider.issueAccessToken(1L);
        when(
            userRepository.existsByIdAndDeletedAtIsNull(1L)
        ).thenReturn(false);
        expectInvalidPasswordUpdateToken(withdrawnToken);

        String activeToken = activeUserToken(1L);
        org.mockito.Mockito.doThrow(
            new BusinessException(
                UserErrorCode.USER_ACCESS_DENIED
            )
        ).when(userService).updatePassword(
            eq(2L),
            eq(1L),
            any(PasswordUpdateRequest.class)
        );

        mockMvc.perform(
                put("/api/v1/users/{userId}/password", 2L)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(activeToken)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(passwordUpdateBody())
            )
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("USER-002"));
    }

    @Test
    void 유효한_액세스_토큰으로_닉네임을_확인하고_참가자_헤더는_무시한다()
        throws Exception {

        String token = activeUserToken(1L);
        when(
            userService.checkNicknameAvailability(
                1L,
                "새닉네임"
            )
        ).thenReturn(new NicknameCheckResponse(
            "새닉네임",
            true
        ));

        mockMvc.perform(
                get("/api/v1/users/nickname/check")
                    .param("nickname", "새닉네임")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
                    .header(
                        "X-Participant-Key",
                        "USER:999"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("NICKNAME_CHECK_SUCCESS")
            )
            .andExpect(
                jsonPath("$.data.available").value(true)
            );

        verify(userService).checkNicknameAvailability(
            1L,
            "새닉네임"
        );
    }

    @Test
    void 닉네임_확인은_토큰이_없으면_401이다()
        throws Exception {

        mockMvc.perform(
                get("/api/v1/users/nickname/check")
                    .param("nickname", "새닉네임")
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code").value("SECURITY-001")
            );
    }

    @Test
    void 만료_변조_리프레시_토큰으로_닉네임을_확인할_수_없다()
        throws Exception {

        String expiredToken = signedToken(
            1L,
            TokenType.ACCESS,
            Instant.now().minusSeconds(1L),
            secretKey()
        );
        String tamperedToken = signedToken(
            1L,
            TokenType.ACCESS,
            Instant.now().plusSeconds(300L),
            Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(
                    "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
                )
            )
        );
        String refreshToken =
            jwtTokenProvider.issueRefreshToken(1L);

        expectInvalidNicknameCheckToken(expiredToken);
        expectInvalidNicknameCheckToken(tamperedToken);
        expectInvalidNicknameCheckToken(refreshToken);
    }

    @Test
    void 탈퇴한_회원의_토큰으로_닉네임을_확인할_수_없다()
        throws Exception {

        String token = jwtTokenProvider.issueAccessToken(1L);
        when(
            userRepository.existsByIdAndDeletedAtIsNull(1L)
        ).thenReturn(false);

        expectInvalidNicknameCheckToken(token);
    }

    @Test
    void OPTIONS_preflight는_차단되지_않는다()
        throws Exception {

        mockMvc.perform(
                options("/api/v1/game-results/me")
                    .header(
                        HttpHeaders.ORIGIN,
                        "http://localhost:5173"
                    )
                    .header(
                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                        "GET"
                    )
                    .header(
                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                        "Authorization"
                    )
            )
            .andExpect(status().isOk())
            .andExpect(
                header().string(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                    "http://localhost:5173"
                )
            );
    }

    private String activeUserToken(Long userId) {
        when(
            userRepository.existsByIdAndDeletedAtIsNull(userId)
        ).thenReturn(true);

        return jwtTokenProvider.issueAccessToken(userId);
    }

    private void expectInvalidToken(String token)
        throws Exception {

        mockMvc.perform(
                get("/api/test/protected")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SECURITY-002"))
            .andExpect(
                jsonPath("$.message")
                    .value("유효하지 않은 액세스 토큰입니다.")
            )
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    private void expectInvalidLogoutToken(String token)
        throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/logout")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SECURITY-002"))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    private void expectInvalidWithdrawalToken(String token)
        throws Exception {

        mockMvc.perform(
                delete("/api/v1/auth/withdraw")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SECURITY-002"))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    private void expectInvalidUserReadToken(String token)
        throws Exception {

        mockMvc.perform(
                get("/api/v1/users/{userId}", 1L)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code").value("SECURITY-002")
            )
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    private void expectInvalidNicknameCheckToken(String token)
        throws Exception {

        mockMvc.perform(
                get("/api/v1/users/nickname/check")
                    .param("nickname", "새닉네임")
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code").value("SECURITY-002")
            )
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    private void expectInvalidPasswordUpdateToken(String token)
        throws Exception {

        mockMvc.perform(
                put("/api/v1/users/{userId}/password", 1L)
                    .header(
                        HttpHeaders.AUTHORIZATION,
                        bearer(token)
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(passwordUpdateBody())
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code").value("SECURITY-002")
            )
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    private static String passwordUpdateBody() {
        return """
            {
              "currentPassword": "password123",
              "newPassword": "newPassword456"
            }
            """;
    }

    private String signedToken(
        Long userId,
        TokenType tokenType,
        Instant expiration,
        SecretKey signingKey
    ) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("tokenType", tokenType.name())
            .issuedAt(Date.from(Instant.now().minusSeconds(60L)))
            .expiration(Date.from(expiration))
            .signWith(signingKey)
            .compact();
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(
            Decoders.BASE64.decode(SECRET_KEY)
        );
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

}
