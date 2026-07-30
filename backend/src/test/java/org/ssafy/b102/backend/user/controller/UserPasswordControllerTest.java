package org.ssafy.b102.backend.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import org.ssafy.b102.backend.user.dto.request.PasswordUpdateRequest;
import org.ssafy.b102.backend.user.exception.UserErrorCode;
import org.ssafy.b102.backend.user.service.UserService;

@ExtendWith(MockitoExtension.class)
class UserPasswordControllerTest {

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
    void successReturns200WithoutDataAndPassesPrincipal()
        throws Exception {

        mockMvc.perform(
            put("/api/v1/users/{userId}/password", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody())
        )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.code")
                    .value("PASSWORD_UPDATE_SUCCESS")
            )
            .andExpect(
                jsonPath("$.message")
                    .value("비밀번호 변경이 완료되었습니다.")
            )
            .andExpect(jsonPath("$.data").doesNotExist());

        ArgumentCaptor<PasswordUpdateRequest> captor =
            ArgumentCaptor.forClass(
                PasswordUpdateRequest.class
            );
        verify(userService).updatePassword(
            eq(USER_ID),
            eq(USER_ID),
            captor.capture()
        );
        assertThat(captor.getValue().getCurrentPassword())
            .isEqualTo("password123");
        assertThat(captor.getValue().getNewPassword())
            .isEqualTo("newPassword456");
    }

    @Test
    void validationFailureReturnsCommon001() throws Exception {
        mockMvc.perform(
            put("/api/v1/users/{userId}/password", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currentPassword": "",
                      "newPassword": "lettersOnly"
                    }
                    """
                )
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-001"));

        verifyNoInteractions(userService);
    }

    @Test
    void unknownFieldReturnsCommon002() throws Exception {
        doAnswer(invocation -> {
            PasswordUpdateRequest request =
                invocation.getArgument(2);
            if (request.hasUnknownFields()) {
                throw new BusinessException(
                    CommonErrorCode.MALFORMED_JSON
                );
            }
            return null;
        }).when(userService).updatePassword(
            eq(USER_ID),
            eq(USER_ID),
            any(PasswordUpdateRequest.class)
        );

        mockMvc.perform(
            put("/api/v1/users/{userId}/password", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currentPassword": "password123",
                      "newPassword": "newPassword456",
                      "email": "other@example.com"
                    }
                    """
                )
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-002"));
    }

    @Test
    void userPasswordErrorsKeepTheirCodes() throws Exception {
        UserErrorCode[] errorCodes = {
            UserErrorCode.CURRENT_PASSWORD_MISMATCH,
            UserErrorCode.PASSWORD_SAME_AS_CURRENT,
            UserErrorCode.PASSWORD_CHANGE_NOT_SUPPORTED
        };
        String[] responseCodes = {
            "USER-007",
            "USER-008",
            "USER-009"
        };

        for (int index = 0; index < errorCodes.length; index++) {
            UserErrorCode errorCode = errorCodes[index];
            String responseCode = responseCodes[index];
            org.mockito.Mockito.reset(userService);
            doAnswer(invocation -> {
                throw new BusinessException(
                    errorCode
                );
            }).when(userService).updatePassword(
                eq(USER_ID),
                eq(USER_ID),
                any(PasswordUpdateRequest.class)
            );

            mockMvc.perform(
                put(
                    "/api/v1/users/{userId}/password",
                    USER_ID
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody())
            )
                .andExpect(status().isBadRequest())
                .andExpect(
                    jsonPath("$.code")
                        .value(responseCode)
                );
        }
    }

    private static String validBody() {
        return """
            {
              "currentPassword": "password123",
              "newPassword": "newPassword456"
            }
            """;
    }
}
