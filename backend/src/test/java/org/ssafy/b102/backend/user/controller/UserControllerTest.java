package org.ssafy.b102.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.user.dto.response.UserResponse;
import org.ssafy.b102.backend.user.enums.ProfileImageCode;
import org.ssafy.b102.backend.user.enums.UserLoginType;
import org.ssafy.b102.backend.user.exception.UserErrorCode;
import org.ssafy.b102.backend.user.service.UserService;

class UserControllerTest {

    private static final Long USER_ID = 1L;
    private static final Instant CREATED_AT =
        Instant.parse("2026-07-30T00:00:00Z");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 본인의_내_정보를_조회한다() throws Exception {
        StubUserService userService = new StubUserService(
            localResponse()
        );

        mockMvc(userService)
            .perform(get("/api/v1/users/{userId}", USER_ID))
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("USER_READ_SUCCESS")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("내 정보 조회가 완료되었습니다.")
            )
            .andExpect(jsonPath("$.data.id").value(USER_ID))
            .andExpect(
                jsonPath("$.data.email")
                    .value("user@example.com")
            )
            .andExpect(
                jsonPath("$.data.nickname")
                    .value("용감한수달0123")
            )
            .andExpect(
                jsonPath("$.data.profileImageCode")
                    .value("PROFILE_1")
            )
            .andExpect(
                jsonPath("$.data.loginType")
                    .value("LOCAL")
            )
            .andExpect(
                jsonPath("$.data.createdAt")
                    .value("2026-07-30T00:00:00Z")
            )
            .andExpect(
                content().string(not(containsString("password")))
            )
            .andExpect(
                content().string(not(containsString("deletedAt")))
            )
            .andExpect(
                content().string(not(containsString("updatedAt")))
            )
            .andExpect(
                content().string(not(containsString("providerUserId")))
            )
            .andExpect(
                content().string(not(containsString("Token")))
            );

        assertThat(userService.requestedUserId)
            .isEqualTo(USER_ID);
        assertThat(userService.authenticatedUserId)
            .isEqualTo(USER_ID);
    }

    @Test
    void 카카오_회원의_email_null을_응답에_포함한다()
        throws Exception {
        mockMvc(new StubUserService(kakaoResponse()))
            .perform(get("/api/v1/users/{userId}", USER_ID))
            .andExpect(status().isOk())
            .andExpect(
                content().string(containsString("\"email\":null"))
            )
            .andExpect(
                jsonPath("$.data.loginType").value("KAKAO")
            );
    }

    @Test
    void 존재하지_않는_회원은_USER_001을_반환한다()
        throws Exception {
        mockMvc(new ThrowingUserService(
            UserErrorCode.USER_NOT_FOUND
        ))
            .perform(get("/api/v1/users/{userId}", USER_ID))
            .andExpect(status().isNotFound())
            .andExpect(
                jsonPath("$.code").value("USER-001")
            );
    }

    @Test
    void 다른_회원_조회는_USER_002를_반환한다()
        throws Exception {
        mockMvc(new ThrowingUserService(
            UserErrorCode.USER_ACCESS_DENIED
        ))
            .perform(get("/api/v1/users/{userId}", 2L))
            .andExpect(status().isForbidden())
            .andExpect(
                jsonPath("$.code").value("USER-002")
            );
    }

    private MockMvc mockMvc(UserService userService) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(USER_ID),
                null,
                List.of()
            )
        );

        return MockMvcBuilders
            .standaloneSetup(new UserController(userService))
            .setCustomArgumentResolvers(
                new AuthenticationPrincipalArgumentResolver()
            )
            .setControllerAdvice(
                new GlobalExceptionHandler()
            )
            .build();
    }

    private static UserResponse localResponse() {
        return new UserResponse(
            USER_ID,
            "user@example.com",
            "용감한수달0123",
            ProfileImageCode.PROFILE_1,
            UserLoginType.LOCAL,
            CREATED_AT
        );
    }

    private static UserResponse kakaoResponse() {
        return new UserResponse(
            USER_ID,
            null,
            "용감한수달0123",
            ProfileImageCode.PROFILE_1,
            UserLoginType.KAKAO,
            CREATED_AT
        );
    }

    private static class StubUserService extends UserService {

        private final UserResponse response;
        private Long requestedUserId;
        private Long authenticatedUserId;

        private StubUserService(UserResponse response) {
            super(null, null);
            this.response = response;
        }

        @Override
        public UserResponse getMyInfo(
            Long requestedUserId,
            Long authenticatedUserId
        ) {
            this.requestedUserId = requestedUserId;
            this.authenticatedUserId = authenticatedUserId;

            return response;
        }
    }

    private static final class ThrowingUserService
        extends StubUserService {

        private final UserErrorCode errorCode;

        private ThrowingUserService(
            UserErrorCode errorCode
        ) {
            super(null);
            this.errorCode = errorCode;
        }

        @Override
        public UserResponse getMyInfo(
            Long requestedUserId,
            Long authenticatedUserId
        ) {
            throw new BusinessException(errorCode);
        }
    }
}
