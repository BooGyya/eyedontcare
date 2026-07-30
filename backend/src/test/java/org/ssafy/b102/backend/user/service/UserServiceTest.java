package org.ssafy.b102.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ssafy.b102.backend.global.common.entity.BaseTimeEntity;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.user.dto.response.UserResponse;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.enums.ProfileImageCode;
import org.ssafy.b102.backend.user.enums.SocialProvider;
import org.ssafy.b102.backend.user.enums.UserLoginType;
import org.ssafy.b102.backend.user.exception.UserErrorCode;
import org.ssafy.b102.backend.user.repository.SocialAccountRepository;
import org.ssafy.b102.backend.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 1L;
    private static final Instant CREATED_AT =
        Instant.parse("2026-07-30T00:00:00Z");

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
            userRepository,
            socialAccountRepository
        );
    }

    @Test
    void 로컬_회원의_내_정보를_조회한다() {
        User user = localUser();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID))
            .thenReturn(Optional.of(user));
        when(socialAccountRepository.findProviderByUserId(USER_ID))
            .thenReturn(Optional.empty());

        UserResponse response =
            userService.getMyInfo(USER_ID, USER_ID);

        assertThat(response.id()).isEqualTo(USER_ID);
        assertThat(response.email())
            .isEqualTo("user@example.com");
        assertThat(response.nickname())
            .isEqualTo("용감한수달0123");
        assertThat(response.profileImageCode())
            .isEqualTo(ProfileImageCode.PROFILE_1);
        assertThat(response.loginType())
            .isEqualTo(UserLoginType.LOCAL);
        assertThat(response.createdAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void 카카오_회원은_email_null과_KAKAO_loginType을_반환한다() {
        User user = socialUser();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID))
            .thenReturn(Optional.of(user));
        when(socialAccountRepository.findProviderByUserId(USER_ID))
            .thenReturn(Optional.of(SocialProvider.KAKAO));

        UserResponse response =
            userService.getMyInfo(USER_ID, USER_ID);

        assertThat(response.email()).isNull();
        assertThat(response.loginType())
            .isEqualTo(UserLoginType.KAKAO);
    }

    @Test
    void email이_null이어도_SocialAccount가_없으면_LOCAL이다() {
        User user = socialUser();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID))
            .thenReturn(Optional.of(user));
        when(socialAccountRepository.findProviderByUserId(USER_ID))
            .thenReturn(Optional.empty());

        UserResponse response =
            userService.getMyInfo(USER_ID, USER_ID);

        assertThat(response.email()).isNull();
        assertThat(response.loginType())
            .isEqualTo(UserLoginType.LOCAL);
    }

    @Test
    void path와_principal_ID가_다르면_조회하지_않고_거부한다() {
        assertThatThrownBy(
            () -> userService.getMyInfo(2L, USER_ID)
        )
            .isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(
                        UserErrorCode.USER_ACCESS_DENIED
                    )
            );

        verifyNoInteractions(
            userRepository,
            socialAccountRepository
        );
    }

    @Test
    void 활성_사용자가_없으면_USER_NOT_FOUND를_반환한다() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> userService.getMyInfo(USER_ID, USER_ID)
        )
            .isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND)
            );

        verify(userRepository)
            .findByIdAndDeletedAtIsNull(USER_ID);
        verifyNoInteractions(socialAccountRepository);
    }

    private static User localUser() {
        User user = User.createLocal(
            "user@example.com",
            "encoded-password",
            "용감한수달0123"
        );
        setUserIdentity(user);

        return user;
    }

    private static User socialUser() {
        User user = User.createSocial("용감한수달0123");
        setUserIdentity(user);

        return user;
    }

    private static void setUserIdentity(User user) {
        setField(User.class, user, "id", USER_ID);
        setField(
            BaseTimeEntity.class,
            user,
            "createdAt",
            CREATED_AT
        );
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
            throw new IllegalStateException(
                "테스트 필드 설정에 실패했습니다.",
                exception
            );
        }
    }
}
