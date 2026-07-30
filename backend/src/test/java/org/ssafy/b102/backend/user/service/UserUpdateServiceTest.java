package org.ssafy.b102.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.ssafy.b102.backend.global.common.entity.BaseTimeEntity;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.CommonErrorCode;
import org.ssafy.b102.backend.user.dto.request.UserUpdateRequest;
import org.ssafy.b102.backend.user.dto.response.UserResponse;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.enums.ProfileImageCode;
import org.ssafy.b102.backend.user.exception.UserErrorCode;
import org.ssafy.b102.backend.user.repository.SocialAccountRepository;
import org.ssafy.b102.backend.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserUpdateServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    private UserService userService;
    private User user;

    @BeforeEach
    void setUp() {
        userService = new UserService(
            userRepository,
            socialAccountRepository
        );
        user = activeUser();
    }

    @Test
    void updatesNicknameAndProfileAndReturnsFullResponse() {
        UserUpdateRequest request = request(
            "NewName",
            "PROFILE_2"
        );
        givenActiveUser();
        when(
            userRepository
                .existsByNicknameAndIdNotAndDeletedAtIsNull(
                    "NewName",
                    USER_ID
                )
        ).thenReturn(false);
        when(socialAccountRepository.findProviderByUserId(USER_ID))
            .thenReturn(Optional.empty());

        UserResponse response = userService.updateMyInfo(
            USER_ID,
            USER_ID,
            request
        );

        assertThat(user.getNickname()).isEqualTo("NewName");
        assertThat(user.getProfileImageCode())
            .isEqualTo(ProfileImageCode.PROFILE_2);
        assertThat(response.nickname()).isEqualTo("NewName");
        assertThat(response.email())
            .isEqualTo("user@example.com");
        verify(userRepository).flush();
    }

    @Test
    void omittedProfileKeepsExistingValue() {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setNickname("NewName");
        givenActiveUser();
        when(
            userRepository
                .existsByNicknameAndIdNotAndDeletedAtIsNull(
                    "NewName",
                    USER_ID
                )
        ).thenReturn(false);
        when(socialAccountRepository.findProviderByUserId(USER_ID))
            .thenReturn(Optional.empty());

        userService.updateMyInfo(USER_ID, USER_ID, request);

        assertThat(user.getProfileImageCode())
            .isEqualTo(ProfileImageCode.PROFILE_1);
    }

    @Test
    void differentPrincipalIsRejectedBeforeRepositoryAccess() {
        assertThatThrownBy(() ->
            userService.updateMyInfo(
                2L,
                USER_ID,
                request("NewName", null)
            )
        ).isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode())
                .isEqualTo(UserErrorCode.USER_ACCESS_DENIED)
        );

        verifyNoInteractions(
            userRepository,
            socialAccountRepository
        );
    }

    @Test
    void emptyRequestIsRejectedWithoutMutation() {
        givenActiveUser();

        assertBusinessError(
            new UserUpdateRequest(),
            UserErrorCode.EMPTY_UPDATE_REQUEST
        );

        assertThat(user.getNickname()).isEqualTo("Original1");
        verify(userRepository, never()).flush();
    }

    @Test
    void unknownFieldIsCommonMalformedJsonWithoutMutation() {
        UserUpdateRequest request = request(
            "NewName",
            "PROFILE_2"
        );
        request.addUnknownField("email", "other@example.com");
        givenActiveUser();

        assertBusinessError(
            request,
            CommonErrorCode.MALFORMED_JSON
        );

        assertThat(user.getNickname()).isEqualTo("Original1");
        verify(userRepository, never()).flush();
    }

    @Test
    void nullNicknameAndInvalidProfileAreRejectedWithoutMutation() {
        UserUpdateRequest nullNickname =
            new UserUpdateRequest();
        nullNickname.setNickname(null);
        givenActiveUser();

        assertBusinessError(
            nullNickname,
            UserErrorCode.INVALID_NICKNAME
        );
        assertThat(user.getNickname()).isEqualTo("Original1");

        UserUpdateRequest invalidProfile =
            new UserUpdateRequest();
        invalidProfile.setProfileImageCode("profile_2");

        assertBusinessError(
            invalidProfile,
            UserErrorCode.INVALID_PROFILE_IMAGE
        );
        assertThat(user.getProfileImageCode())
            .isEqualTo(ProfileImageCode.PROFILE_1);
        verify(userRepository, never()).flush();
    }

    @Test
    void duplicateNicknameIsRejectedWithoutChangingProfile() {
        UserUpdateRequest request = request(
            "Duplicated",
            "PROFILE_2"
        );
        givenActiveUser();
        when(
            userRepository
                .existsByNicknameAndIdNotAndDeletedAtIsNull(
                    "Duplicated",
                    USER_ID
                )
        ).thenReturn(true);

        assertBusinessError(
            request,
            UserErrorCode.NICKNAME_DUPLICATED
        );

        assertThat(user.getNickname()).isEqualTo("Original1");
        assertThat(user.getProfileImageCode())
            .isEqualTo(ProfileImageCode.PROFILE_1);
        verify(userRepository, never()).flush();
    }

    @Test
    void nicknameConstraintIsMappedButUnexpectedConstraintIsNot() {
        UserUpdateRequest request = request(
            "NewName",
            "PROFILE_2"
        );
        givenActiveUser();
        doThrow(constraintViolation("uk_users_nickname"))
            .when(userRepository).flush();

        assertBusinessError(
            request,
            UserErrorCode.NICKNAME_DUPLICATED
        );

        DataIntegrityViolationException unexpected =
            constraintViolation("uk_users_email");
        doThrow(unexpected).when(userRepository).flush();

        assertThatThrownBy(() ->
            userService.updateMyInfo(
                USER_ID,
                USER_ID,
                request
            )
        ).isSameAs(unexpected);
    }

    private void givenActiveUser() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID))
            .thenReturn(Optional.of(user));
    }

    private void assertBusinessError(
        UserUpdateRequest request,
        Object expectedErrorCode
    ) {
        assertThatThrownBy(() ->
            userService.updateMyInfo(
                USER_ID,
                USER_ID,
                request
            )
        ).isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.getErrorCode())
                .isEqualTo(expectedErrorCode)
        );
    }

    private static UserUpdateRequest request(
        String nickname,
        String profileImageCode
    ) {
        UserUpdateRequest request = new UserUpdateRequest();
        if (nickname != null) {
            request.setNickname(nickname);
        }
        if (profileImageCode != null) {
            request.setProfileImageCode(profileImageCode);
        }
        return request;
    }

    private static User activeUser() {
        User user = User.createLocal(
            "user@example.com",
            "encoded-password",
            "Original1"
        );
        setField(User.class, user, "id", USER_ID);
        setField(
            BaseTimeEntity.class,
            user,
            "createdAt",
            Instant.parse("2026-07-30T00:00:00Z")
        );
        return user;
    }

    private static void setField(
        Class<?> declaringClass,
        Object target,
        String fieldName,
        Object value
    ) {
        try {
            Field field =
                declaringClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (
            NoSuchFieldException |
            IllegalAccessException exception
        ) {
            throw new IllegalStateException(exception);
        }
    }

    private static DataIntegrityViolationException
    constraintViolation(String constraintName) {
        return new DataIntegrityViolationException(
            "constraint violation",
            new ConstraintViolationException(
                "constraint violation",
                null,
                constraintName
            )
        );
    }
}
