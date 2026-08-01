package org.ssafy.b102.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.CommonErrorCode;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.user.dto.request.UserUpdateRequest;
import org.ssafy.b102.backend.user.dto.response.UserResponse;
import org.ssafy.b102.backend.user.enums.ProfileImageCode;
import org.ssafy.b102.backend.user.enums.UserLoginType;
import org.ssafy.b102.backend.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserUpdateControllerTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(USER_ID),
                null,
                List.of()
            )
        );
        mockMvc = MockMvcBuilders
            .standaloneSetup(new UserController(userService))
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
    void patchReturnsUpdatedUserAndPassesPrincipal() throws Exception {
        when(
            userService.updateMyInfo(
                eq(USER_ID),
                eq(USER_ID),
                any(UserUpdateRequest.class)
            )
        ).thenReturn(response());

        mockMvc.perform(
            patch("/api/v1/users/{userId}", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "nickname": "NewName",
                      "profileImageCode": "PROFILE_2"
                    }
                    """
                )
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("USER_UPDATE_SUCCESS")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("내 정보 수정이 완료되었습니다.")
            )
            .andExpect(
                jsonPath("$.data.nickname").value("NewName")
            )
            .andExpect(
                jsonPath("$.data.profileImageCode")
                    .value("PROFILE_2")
            );

        ArgumentCaptor<UserUpdateRequest> captor =
            ArgumentCaptor.forClass(UserUpdateRequest.class);
        org.mockito.Mockito.verify(userService).updateMyInfo(
            eq(USER_ID),
            eq(USER_ID),
            captor.capture()
        );
        assertThat(captor.getValue().getNickname())
            .isEqualTo("NewName");
    }

    @Test
    void emptyAndUnknownRequestsReturnSpecifiedErrors()
        throws Exception {
        when(
            userService.updateMyInfo(
                eq(USER_ID),
                eq(USER_ID),
                any(UserUpdateRequest.class)
            )
        )
            .thenThrow(
                new BusinessException(
                    org.ssafy.b102.backend.user.exception
                        .UserErrorCode.EMPTY_UPDATE_REQUEST
                )
            )
            .thenThrow(
                new BusinessException(
                    CommonErrorCode.MALFORMED_JSON
                )
            );

        mockMvc.perform(
            patch("/api/v1/users/{userId}", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("USER-006"));

        mockMvc.perform(
            patch("/api/v1/users/{userId}", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"other@example.com\"}")
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-002"));
    }

    @Test
    void malformedJsonReturnsCommon002() throws Exception {
        mockMvc.perform(
            patch("/api/v1/users/{userId}", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":")
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-002"));
    }

    private static UserResponse response() {
        return new UserResponse(
            USER_ID,
            "user@example.com",
            "NewName",
            ProfileImageCode.PROFILE_2,
            UserLoginType.LOCAL,
            Instant.parse("2026-07-30T00:00:00Z")
        );
    }
}
