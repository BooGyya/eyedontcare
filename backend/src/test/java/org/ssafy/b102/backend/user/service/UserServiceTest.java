package org.ssafy.b102.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ssafy.b102.backend.global.common.entity.BaseTimeEntity;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.user.dto.response.UserResponse;
import org.ssafy.b102.backend.user.dto.response.NicknameCheckResponse;
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

    @Test
    void 사용할_수_있는_닉네임은_available_true다() {
        givenActiveUser();
        when(
            userRepository
                .existsByNicknameAndIdNotAndDeletedAtIsNull(
                    "새닉네임1",
                    USER_ID
                )
        ).thenReturn(false);

        NicknameCheckResponse response =
            userService.checkNicknameAvailability(
                USER_ID,
                "새닉네임1"
            );

        assertThat(response.nickname()).isEqualTo("새닉네임1");
        assertThat(response.available()).isTrue();
    }

    @Test
    void 다른_활성_회원의_닉네임은_available_false다() {
        givenActiveUser();
        when(
            userRepository
                .existsByNicknameAndIdNotAndDeletedAtIsNull(
                    "이미사용중",
                    USER_ID
                )
        ).thenReturn(true);

        NicknameCheckResponse response =
            userService.checkNicknameAvailability(
                USER_ID,
                "이미사용중"
            );

        assertThat(response.available()).isFalse();
    }

    @Test
    void 현재_닉네임과_탈퇴_회원_닉네임은_available_true다() {
        givenActiveUser();
        when(
            userRepository
                .existsByNicknameAndIdNotAndDeletedAtIsNull(
                    "용감한수달1",
                    USER_ID
                )
        ).thenReturn(false);

        NicknameCheckResponse response =
            userService.checkNicknameAvailability(
                USER_ID,
                "용감한수달1"
            );

        assertThat(response.available()).isTrue();
        verify(userRepository)
            .existsByNicknameAndIdNotAndDeletedAtIsNull(
                "용감한수달1",
                USER_ID
            );
    }

    @Test
    void 영문_대소문자를_변환하지_않고_exact_match로_조회한다() {
        givenActiveUser();
        when(
            userRepository
                .existsByNicknameAndIdNotAndDeletedAtIsNull(
                    "Miji",
                    USER_ID
                )
        ).thenReturn(false);

        NicknameCheckResponse response =
            userService.checkNicknameAvailability(
                USER_ID,
                "Miji"
            );

        assertThat(response.nickname()).isEqualTo("Miji");
        verify(userRepository)
            .existsByNicknameAndIdNotAndDeletedAtIsNull(
                "Miji",
                USER_ID
            );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "가나",
        "가나다라마바사아자차",
        "Ab",
        "12",
        "한글Ab12"
    })
    void 허용된_형식의_닉네임을_검사한다(String nickname) {
        givenActiveUser();
        when(
            userRepository
                .existsByNicknameAndIdNotAndDeletedAtIsNull(
                    nickname,
                    USER_ID
                )
        ).thenReturn(false);

        NicknameCheckResponse response =
            userService.checkNicknameAvailability(
                USER_ID,
                nickname
            );

        assertThat(response.nickname()).isEqualTo(nickname);
        assertThat(response.available()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "",
        "가",
        "가나다라마바사아자차카",
        " 앞공백",
        "뒤공백 ",
        "내부 공백",
        "탭\t문자",
        "개행\n문자",
        "특수!",
        "이모지😀",
        "ひらがな"
    })
    void 허용되지_않은_닉네임은_trim하지_않고_USER_003을_반환한다(
        String nickname
    ) {
        givenActiveUser();

        assertThatThrownBy(
            () -> userService.checkNicknameAvailability(
                USER_ID,
                nickname
            )
        )
            .isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(
                        UserErrorCode.INVALID_NICKNAME
                    )
            );

        verify(userRepository)
            .findByIdAndDeletedAtIsNull(USER_ID);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void 활성_사용자가_없으면_닉네임_중복을_조회하지_않는다() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> userService.checkNicknameAvailability(
                USER_ID,
                "새닉네임"
            )
        )
            .isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(UserErrorCode.USER_NOT_FOUND)
            );

        verify(userRepository)
            .findByIdAndDeletedAtIsNull(USER_ID);
        verifyNoMoreInteractions(userRepository);
        verifyNoInteractions(socialAccountRepository);
    }

    private void givenActiveUser() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID))
            .thenReturn(Optional.of(localUser()));
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
