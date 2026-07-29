package org.ssafy.b102.backend.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ssafy.b102.backend.auth.dto.request.LoginRequest;
import org.ssafy.b102.backend.auth.dto.request.ReissueRequest;
import org.ssafy.b102.backend.auth.dto.request.SignupRequest;
import org.ssafy.b102.backend.auth.dto.response.TokenResponse;
import org.ssafy.b102.backend.auth.exception.AuthErrorCode;
import org.ssafy.b102.backend.auth.service.AuthService;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;

class AuthControllerTest {

    private static final Long USER_ID = 1L;

    private MockMvc mockMvc;
    private StubAuthService authService;

    @BeforeEach
    void setUp() {
        authService = new StubAuthService();

        mockMvc = MockMvcBuilders
            .standaloneSetup(
                new AuthController(authService)
            )
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver()
            )
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 회원가입에_성공하면_201과_토큰을_반환한다()
        throws Exception {

        String requestBody = """
			{
			  "email": "user@example.com",
			  "password": "password123"
			}
			""";

        mockMvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isCreated())
            .andExpect(
                jsonPath("$.code")
                    .value("AUTH_SIGNUP_SUCCESS")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("회원가입이 완료되었습니다.")
            )
            .andExpect(
                jsonPath("$.data.accessToken")
                    .value("access-token")
            )
            .andExpect(
                jsonPath("$.data.refreshToken")
                    .value("refresh-token")
            );
    }

    @Test
    void 이메일이_중복되면_409를_반환한다()
        throws Exception {

        String requestBody = """
			{
			  "email": "duplicate@example.com",
			  "password": "password123"
			}
			""";

        mockMvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isConflict())
            .andExpect(
                jsonPath("$.code")
                    .value("AUTH-001")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("이미 사용 중인 이메일입니다.")
            )
            .andExpect(
                jsonPath("$.data").doesNotExist()
            );
    }

    @Test
    void 이메일_형식이_잘못되면_400을_반환한다()
        throws Exception {

        String requestBody = """
			{
			  "email": "invalid-email",
			  "password": "password123"
			}
			""";

        mockMvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("COMMON-001")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("요청 값이 올바르지 않습니다.")
            )
            .andExpect(
                jsonPath("$.data.fieldErrors[0].field")
                    .value("email")
            )
            .andExpect(
                jsonPath("$.data.fieldErrors[0].reason")
                    .value("올바른 이메일 형식이 아닙니다.")
            );
    }

    @Test
    void 비밀번호가_8자보다_짧으면_400을_반환한다()
        throws Exception {

        String requestBody = """
			{
			  "email": "user@example.com",
			  "password": "pass123"
			}
			""";

        mockMvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("COMMON-001")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("요청 값이 올바르지 않습니다.")
            )
            .andExpect(
                jsonPath("$.data.fieldErrors[0].field")
                    .value("password")
            )
            .andExpect(
                jsonPath("$.data.fieldErrors[0].reason")
                    .value("비밀번호는 8자 이상 16자 이하여야 합니다.")
            );
    }

    @Test
    void 비밀번호가_16자를_초과하면_400을_반환한다()
        throws Exception {

        String requestBody = """
			{
			  "email": "user@example.com",
			  "password": "password123456789"
			}
			""";

        mockMvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("COMMON-001")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("요청 값이 올바르지 않습니다.")
            )
            .andExpect(
                jsonPath("$.data.fieldErrors[0].field")
                    .value("password")
            )
            .andExpect(
                jsonPath("$.data.fieldErrors[0].reason")
                    .value("비밀번호는 8자 이상 16자 이하여야 합니다.")
            );
    }

    @Test
    void 비밀번호에_숫자가_없으면_400을_반환한다()
        throws Exception {

        String requestBody = """
			{
			  "email": "user@example.com",
			  "password": "password"
			}
			""";

        mockMvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("COMMON-001")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("요청 값이 올바르지 않습니다.")
            )
            .andExpect(
                jsonPath("$.data.fieldErrors[0].field")
                    .value("password")
            )
            .andExpect(
                jsonPath("$.data.fieldErrors[0].reason")
                    .value("비밀번호에는 영문과 숫자가 각각 하나 이상 포함되어야 합니다.")
            );
    }

    @Test
    void 비밀번호에_영문이_없으면_400을_반환한다()
        throws Exception {

        String requestBody = """
			{
			  "email": "user@example.com",
			  "password": "12345678"
			}
			""";

        mockMvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("COMMON-001")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("요청 값이 올바르지 않습니다.")
            )
            .andExpect(
                jsonPath("$.data.fieldErrors[0].field")
                    .value("password")
            )
            .andExpect(
                jsonPath("$.data.fieldErrors[0].reason")
                    .value("비밀번호에는 영문과 숫자가 각각 하나 이상 포함되어야 합니다.")
            );
    }

    @Test
    void 요청_본문이_잘못된_JSON이면_400을_반환한다()
        throws Exception {

        String malformedJson = """
			{
			  "email": "user@example.com",
			  "password":
			}
			""";

        mockMvc.perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(malformedJson)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("COMMON-002")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("요청 본문을 읽을 수 없습니다.")
            )
            .andExpect(
                jsonPath("$.data").doesNotExist()
            );
    }

    @Test
    void 로그인에_성공하면_200과_토큰을_반환한다()
        throws Exception {

        String requestBody = """
			{
			  "email": "user@example.com",
			  "password": "password123"
			}
			""";

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("AUTH_LOGIN_SUCCESS")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("로그인이 완료되었습니다.")
            )
            .andExpect(
                jsonPath("$.data.accessToken")
                    .value("access-token")
            )
            .andExpect(
                jsonPath("$.data.refreshToken")
                    .value("refresh-token")
            );
    }

    @Test
    void 로그인_인증에_실패하면_401을_반환한다()
        throws Exception {

        String requestBody = """
			{
			  "email": "unknown@example.com",
			  "password": "password123"
			}
			""";

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(
                jsonPath("$.code")
                    .value("AUTH-003")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("이메일 또는 비밀번호가 올바르지 않습니다.")
            )
            .andExpect(
                jsonPath("$.data").doesNotExist()
            );
    }

    @Test
    void 로그인_요청_검증에_실패하면_400을_반환한다()
        throws Exception {

        String requestBody = """
			{
			  "email": "invalid-email",
			  "password": "password123"
			}
			""";

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("COMMON-001")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("요청 값이 올바르지 않습니다.")
            );
    }

    @Test
    void 로그인_요청_본문이_잘못된_JSON이면_400을_반환한다()
        throws Exception {

        String malformedJson = """
			{
			  "email": "user@example.com",
			  "password":
			}
			""";

        mockMvc.perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(malformedJson)
            )
            .andExpect(status().isBadRequest())
            .andExpect(
                jsonPath("$.code")
                    .value("COMMON-002")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("요청 본문을 읽을 수 없습니다.")
            );
    }

    @Test
    void 토큰_재발급에_성공하면_200과_새_토큰을_반환한다()
        throws Exception {

        String requestBody = """
            {
              "refreshToken": "refresh-token"
            }
            """;

        mockMvc.perform(
                post("/api/v1/auth/reissue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("AUTH_REISSUE_SUCCESS")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("토큰 재발급이 완료되었습니다.")
            )
            .andExpect(
                jsonPath("$.data.accessToken")
                    .value("new-access-token")
            )
            .andExpect(
                jsonPath("$.data.refreshToken")
                    .value("new-refresh-token")
            );
    }

    @Test
    void 리프레시_토큰이_비어_있으면_400을_반환한다()
        throws Exception {

        String requestBody = """
            {
              "refreshToken": " "
            }
            """;

        mockMvc.perform(
                post("/api/v1/auth/reissue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-001"));
    }

    @Test
    void 리프레시_토큰이_누락되면_400을_반환한다()
        throws Exception {

        mockMvc.perform(
                post("/api/v1/auth/reissue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-001"));
    }

    @Test
    void 유효하지_않은_리프레시_토큰이면_401을_반환한다()
        throws Exception {

        String requestBody = """
            {
              "refreshToken": "invalid-refresh-token"
            }
            """;

        mockMvc.perform(
                post("/api/v1/auth/reissue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-004"))
            .andExpect(
                jsonPath("$.message")
                    .value("유효하지 않은 리프레시 토큰입니다.")
            )
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 액세스_토큰을_재발급_본문으로_보내면_401을_반환한다()
        throws Exception {

        String requestBody = """
            {
              "refreshToken": "access-token"
            }
            """;

        mockMvc.perform(
                post("/api/v1/auth/reissue")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody)
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-004"));
    }

    @Test
    void 인증된_principal로_요청_본문_없이_로그아웃한다()
        throws Exception {

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(USER_ID),
                null,
                List.of()
            )
        );

        mockMvc.perform(
                post("/api/v1/auth/logout")
                    .header("X-Participant-Key", "USER:999")
                    .header("X-User-Id", "999")
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("AUTH_LOGOUT_SUCCESS")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("로그아웃에 성공했습니다.")
            )
            .andExpect(jsonPath("$.data").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(
            authService.logoutUserId
        ).isEqualTo(USER_ID);
    }

    @Test
    void 인증된_principal로_요청_본문_없이_회원_탈퇴한다()
        throws Exception {

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(USER_ID),
                null,
                List.of()
            )
        );

        mockMvc.perform(
                delete("/api/v1/auth/withdraw")
                    .header("X-Participant-Key", "USER:999")
                    .header("X-User-Id", "999")
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("AUTH_WITHDRAWAL_SUCCESS")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("회원 탈퇴가 완료되었습니다.")
            )
            .andExpect(jsonPath("$.data").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(
            authService.withdrawalUserId
        ).isEqualTo(USER_ID);
    }

    private static final class StubAuthService
        extends AuthService {

        private Long logoutUserId;
        private Long withdrawalUserId;

        private StubAuthService() {
            super(
                null,
                null,
                null,
                null,
                null
            );
        }

        @Override
        public TokenResponse signup(SignupRequest request) {
            if (
                "duplicate@example.com"
                    .equals(request.email())
            ) {
                throw new BusinessException(
                    AuthErrorCode.EMAIL_ALREADY_EXISTS
                );
            }

            return new TokenResponse(
                "access-token",
                "refresh-token"
            );
        }

        @Override
        public TokenResponse login(LoginRequest request) {
            if (
                "unknown@example.com"
                    .equals(request.email())
            ) {
                throw new BusinessException(
                    AuthErrorCode.INVALID_CREDENTIALS
                );
            }

            return new TokenResponse(
                "access-token",
                "refresh-token"
            );
        }

        @Override
        public TokenResponse reissue(ReissueRequest request) {
            if (
                !"refresh-token".equals(
                    request.refreshToken()
                )
            ) {
                throw new BusinessException(
                    AuthErrorCode.INVALID_REFRESH_TOKEN
                );
            }

            return new TokenResponse(
                "new-access-token",
                "new-refresh-token"
            );
        }

        @Override
        public void logout(Long userId) {
            logoutUserId = userId;
        }

        @Override
        public void withdraw(Long userId) {
            withdrawalUserId = userId;
        }
    }
}
