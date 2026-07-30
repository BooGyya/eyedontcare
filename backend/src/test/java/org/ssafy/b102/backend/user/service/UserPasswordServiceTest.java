package org.ssafy.b102.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.ssafy.b102.backend.auth.repository.RefreshTokenStore;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.CommonErrorCode;
import org.ssafy.b102.backend.user.dto.request.PasswordUpdateRequest;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.enums.SocialProvider;
import org.ssafy.b102.backend.user.exception.UserErrorCode;
import org.ssafy.b102.backend.user.repository.SocialAccountRepository;
import org.ssafy.b102.backend.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserPasswordServiceTest {

    private static final Long USER_ID = 1L;
    private static final String CURRENT_PASSWORD =
        "password123";
    private static final String NEW_PASSWORD =
        "newPassword456";
    private static final String CURRENT_HASH =
        "current-hash";
    private static final String NEW_HASH = "new-hash";

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    private UserService userService;
    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(
            userRepository,
            socialAccountRepository,
            passwordEncoder,
            refreshTokenStore
        );
        user = localUser(CURRENT_HASH);
    }

    @Test
    void localUserChangesEncodedPasswordAndDeletesRefreshToken() {
        givenActiveUser();
        givenValidPasswordChecks();
        when(passwordEncoder.encode(NEW_PASSWORD))
            .thenReturn(NEW_HASH);

        userService.updatePassword(
            USER_ID,
            USER_ID,
            request()
        );

        assertThat(user.getPasswordHash()).isEqualTo(NEW_HASH);
        assertThat(user.getPasswordHash())
            .isNotEqualTo(NEW_PASSWORD);
        verify(refreshTokenStore).deleteByUserId(USER_ID);
        verify(userRepository, never()).save(user);
    }

    @Test
    void differentPrincipalIsRejectedBeforeRepositoryAccess() {
        assertError(
            2L,
            USER_ID,
            request(),
            UserErrorCode.USER_ACCESS_DENIED
        );

        verifyNoInteractions(
            userRepository,
            socialAccountRepository,
            passwordEncoder,
            refreshTokenStore
        );
    }

    @Test
    void missingActiveUserIsUser001() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID))
            .thenReturn(Optional.empty());

        assertError(
            USER_ID,
            USER_ID,
            request(),
            UserErrorCode.USER_NOT_FOUND
        );

        verifyNoInteractions(
            socialAccountRepository,
            passwordEncoder,
            refreshTokenStore
        );
    }

    @Test
    void unknownFieldIsCommon002BeforeAccountChecks() {
        PasswordUpdateRequest request = request();
        request.addUnknownField("email", "other@example.com");
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID))
            .thenReturn(Optional.of(user));

        assertError(
            USER_ID,
            USER_ID,
            request,
            CommonErrorCode.MALFORMED_JSON
        );

        verifyNoInteractions(
            socialAccountRepository,
            passwordEncoder,
            refreshTokenStore
        );
        assertThat(user.getPasswordHash())
            .isEqualTo(CURRENT_HASH);
    }

    @Test
    void allNonLocalAccountStatesAreUser009() {
        givenActiveUser();
        when(socialAccountRepository.findProviderByUserId(USER_ID))
            .thenReturn(Optional.of(SocialProvider.KAKAO));

        assertError(
            USER_ID,
            USER_ID,
            request(),
            UserErrorCode.PASSWORD_CHANGE_NOT_SUPPORTED
        );

        User socialWithoutPassword = localUser(null);
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID))
            .thenReturn(Optional.of(socialWithoutPassword));

        assertError(
            USER_ID,
            USER_ID,
            request(),
            UserErrorCode.PASSWORD_CHANGE_NOT_SUPPORTED
        );

        User noPassword = localUser(null);
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID))
            .thenReturn(Optional.of(noPassword));
        when(socialAccountRepository.findProviderByUserId(USER_ID))
            .thenReturn(Optional.empty());

        assertError(
            USER_ID,
            USER_ID,
            request(),
            UserErrorCode.PASSWORD_CHANGE_NOT_SUPPORTED
        );

        verifyNoInteractions(passwordEncoder, refreshTokenStore);
    }

    @Test
    void emailNullDoesNotAffectLocalAccountDecision() {
        setField(user, "email", null);
        givenActiveUser();
        givenValidPasswordChecks();
        when(passwordEncoder.encode(NEW_PASSWORD))
            .thenReturn(NEW_HASH);

        userService.updatePassword(
            USER_ID,
            USER_ID,
            request()
        );

        assertThat(user.getPasswordHash()).isEqualTo(NEW_HASH);
        verify(refreshTokenStore).deleteByUserId(USER_ID);
    }

    @Test
    void currentPasswordMismatchIsUser007WithoutMutation() {
        givenActiveUser();
        when(
            passwordEncoder.matches(
                CURRENT_PASSWORD,
                CURRENT_HASH
            )
        ).thenReturn(false);

        assertError(
            USER_ID,
            USER_ID,
            request(),
            UserErrorCode.CURRENT_PASSWORD_MISMATCH
        );

        assertThat(user.getPasswordHash())
            .isEqualTo(CURRENT_HASH);
        verify(passwordEncoder, never()).encode(NEW_PASSWORD);
        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void sameNewPasswordIsUser008WithoutMutation() {
        givenActiveUser();
        when(
            passwordEncoder.matches(
                CURRENT_PASSWORD,
                CURRENT_HASH
            )
        ).thenReturn(true);
        when(
            passwordEncoder.matches(
                NEW_PASSWORD,
                CURRENT_HASH
            )
        ).thenReturn(true);

        assertError(
            USER_ID,
            USER_ID,
            request(),
            UserErrorCode.PASSWORD_SAME_AS_CURRENT
        );

        assertThat(user.getPasswordHash())
            .isEqualTo(CURRENT_HASH);
        verify(passwordEncoder, never()).encode(NEW_PASSWORD);
        verifyNoInteractions(refreshTokenStore);
    }

    @Test
    void redisFailureIsPropagatedForTransactionRollback() {
        RuntimeException redisFailure =
            new RuntimeException("redis unavailable");
        givenActiveUser();
        givenValidPasswordChecks();
        when(passwordEncoder.encode(NEW_PASSWORD))
            .thenReturn(NEW_HASH);
        doThrow(redisFailure)
            .when(refreshTokenStore)
            .deleteByUserId(USER_ID);

        assertThatThrownBy(() ->
            userService.updatePassword(
                USER_ID,
                USER_ID,
                request()
            )
        ).isSameAs(redisFailure);
    }

    private void givenActiveUser() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID))
            .thenReturn(Optional.of(user));
        when(socialAccountRepository.findProviderByUserId(USER_ID))
            .thenReturn(Optional.empty());
    }

    private void givenValidPasswordChecks() {
        when(
            passwordEncoder.matches(
                CURRENT_PASSWORD,
                CURRENT_HASH
            )
        ).thenReturn(true);
        when(
            passwordEncoder.matches(
                NEW_PASSWORD,
                CURRENT_HASH
            )
        ).thenReturn(false);
    }

    private void assertError(
        Long requestedUserId,
        Long authenticatedUserId,
        PasswordUpdateRequest request,
        Object errorCode
    ) {
        assertThatThrownBy(() ->
            userService.updatePassword(
                requestedUserId,
                authenticatedUserId,
                request
            )
        ).isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode())
                .isEqualTo(errorCode)
        );
    }

    private static PasswordUpdateRequest request() {
        PasswordUpdateRequest request =
            new PasswordUpdateRequest();
        request.setCurrentPassword(CURRENT_PASSWORD);
        request.setNewPassword(NEW_PASSWORD);
        return request;
    }

    private static User localUser(String passwordHash) {
        User user = User.createSocial("Nickname1");
        setField(user, "id", USER_ID);
        setField(user, "email", "user@example.com");
        setField(user, "passwordHash", passwordHash);
        return user;
    }

    private static void setField(
        User user,
        String fieldName,
        Object value
    ) {
        try {
            Field field = User.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(user, value);
        } catch (
            NoSuchFieldException |
            IllegalAccessException exception
        ) {
            throw new IllegalStateException(exception);
        }
    }
}
