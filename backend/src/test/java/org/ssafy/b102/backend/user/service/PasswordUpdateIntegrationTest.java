package org.ssafy.b102.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.ssafy.b102.backend.auth.dto.request.LoginRequest;
import org.ssafy.b102.backend.auth.dto.request.ReissueRequest;
import org.ssafy.b102.backend.auth.dto.request.SignupRequest;
import org.ssafy.b102.backend.auth.dto.response.TokenResponse;
import org.ssafy.b102.backend.auth.exception.AuthErrorCode;
import org.ssafy.b102.backend.auth.repository.RefreshTokenStore;
import org.ssafy.b102.backend.auth.service.AuthService;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.user.dto.request.PasswordUpdateRequest;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.repository.UserRepository;

@SpringBootTest
class PasswordUpdateIntegrationTest {

    private static final String CURRENT_PASSWORD =
        "password123";
    private static final String NEW_PASSWORD =
        "newPassword456";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Long createdUserId;

    @AfterEach
    void cleanUp() {
        if (createdUserId != null) {
            refreshTokenStore.deleteByUserId(createdUserId);
            userRepository.deleteById(createdUserId);
        }
    }

    @Test
    void passwordChangeUpdatesDbAndInvalidatesRefreshToken() {
        String email =
            "password-" + UUID.randomUUID() + "@example.com";
        TokenResponse signupTokens = authService.signup(
            new SignupRequest(email, CURRENT_PASSWORD)
        );
        User user = userRepository
            .findByEmailAndDeletedAtIsNull(email)
            .orElseThrow();
        createdUserId = user.getId();

        userService.updatePassword(
            createdUserId,
            createdUserId,
            request()
        );

        User updatedUser = userRepository
            .findById(createdUserId)
            .orElseThrow();
        assertThat(
            passwordEncoder.matches(
                NEW_PASSWORD,
                updatedUser.getPasswordHash()
            )
        ).isTrue();
        assertThat(
            passwordEncoder.matches(
                CURRENT_PASSWORD,
                updatedUser.getPasswordHash()
            )
        ).isFalse();
        assertThat(
            refreshTokenStore.findByUserId(createdUserId)
        ).isEmpty();

        assertAuthError(
            () -> authService.reissue(
                new ReissueRequest(
                    signupTokens.refreshToken()
                )
            ),
            AuthErrorCode.INVALID_REFRESH_TOKEN
        );
        assertAuthError(
            () -> authService.login(
                new LoginRequest(email, CURRENT_PASSWORD)
            ),
            AuthErrorCode.INVALID_CREDENTIALS
        );

        TokenResponse loginTokens = authService.login(
            new LoginRequest(email, NEW_PASSWORD)
        );
        assertThat(loginTokens.accessToken()).isNotBlank();
        assertThat(loginTokens.refreshToken()).isNotBlank();
    }

    private static PasswordUpdateRequest request() {
        PasswordUpdateRequest request =
            new PasswordUpdateRequest();
        request.setCurrentPassword(CURRENT_PASSWORD);
        request.setNewPassword(NEW_PASSWORD);
        return request;
    }

    private static void assertAuthError(
        org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
        AuthErrorCode errorCode
    ) {
        assertThatThrownBy(call)
            .isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(errorCode)
            );
    }
}
