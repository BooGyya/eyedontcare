package org.ssafy.b102.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.ssafy.b102.backend.auth.dto.request.SignupRequest;
import org.ssafy.b102.backend.auth.dto.response.SignupResponse;
import org.ssafy.b102.backend.auth.exception.AuthErrorCode;
import org.ssafy.b102.backend.auth.repository.RefreshTokenStore;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.global.security.jwt.TokenPair;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.repository.UserRepository;
import org.ssafy.b102.backend.user.util.RandomNicknameGenerator;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Long USER_ID = 1L;
    private static final String RAW_PASSWORD = "password123";
    private static final String PASSWORD_HASH = "encoded-password";
    private static final String NICKNAME = "다정한수달0001";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RandomNicknameGenerator randomNicknameGenerator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository,
            randomNicknameGenerator,
            passwordEncoder,
            jwtTokenProvider,
            refreshTokenStore
        );
    }

    @Test
    void 회원가입에_성공하면_토큰을_반환한다() {
        SignupRequest request = new SignupRequest(
            " USER@Example.COM ",
            RAW_PASSWORD
        );

        when(
            userRepository.existsByEmailAndDeletedAtIsNull(
                "user@example.com"
            )
        ).thenReturn(false);

        when(randomNicknameGenerator.generate())
            .thenReturn(NICKNAME);

        when(
            userRepository.existsByNicknameAndDeletedAtIsNull(
                NICKNAME
            )
        ).thenReturn(false);

        when(passwordEncoder.encode(RAW_PASSWORD))
            .thenReturn(PASSWORD_HASH);

        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                setUserId(user, USER_ID);
                return user;
            });

        when(jwtTokenProvider.issueTokenPair(USER_ID))
            .thenReturn(
                new TokenPair(
                    ACCESS_TOKEN,
                    REFRESH_TOKEN
                )
            );

        SignupResponse response = authService.signup(request);

        assertThat(response.accessToken())
            .isEqualTo(ACCESS_TOKEN);

        assertThat(response.refreshToken())
            .isEqualTo(REFRESH_TOKEN);

        ArgumentCaptor<User> userCaptor =
            ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail())
            .isEqualTo("user@example.com");

        assertThat(savedUser.getPasswordHash())
            .isEqualTo(PASSWORD_HASH);

        assertThat(savedUser.getNickname())
            .isEqualTo(NICKNAME);

        verify(refreshTokenStore).save(
            USER_ID,
            REFRESH_TOKEN
        );
    }

    @Test
    void 이미_사용_중인_이메일이면_회원가입에_실패한다() {
        SignupRequest request = new SignupRequest(
            "user@example.com",
            RAW_PASSWORD
        );

        when(
            userRepository.existsByEmailAndDeletedAtIsNull(
                "user@example.com"
            )
        ).thenReturn(true);

        assertThatThrownBy(
            () -> authService.signup(request)
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException =
                    (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(
                        AuthErrorCode.EMAIL_ALREADY_EXISTS
                    );
            });

        verify(userRepository, never())
            .save(any(User.class));

        verify(refreshTokenStore, never())
            .save(any(), any());
    }

    @Test
    void 닉네임이_중복되면_다시_생성한다() {
        SignupRequest request = new SignupRequest(
            "user@example.com",
            RAW_PASSWORD
        );

        String duplicatedNickname = "졸린판다0001";
        String uniqueNickname = "명랑한토끼0002";

        when(
            userRepository.existsByEmailAndDeletedAtIsNull(
                "user@example.com"
            )
        ).thenReturn(false);

        when(randomNicknameGenerator.generate())
            .thenReturn(
                duplicatedNickname,
                uniqueNickname
            );

        when(
            userRepository.existsByNicknameAndDeletedAtIsNull(
                duplicatedNickname
            )
        ).thenReturn(true);

        when(
            userRepository.existsByNicknameAndDeletedAtIsNull(
                uniqueNickname
            )
        ).thenReturn(false);

        when(passwordEncoder.encode(RAW_PASSWORD))
            .thenReturn(PASSWORD_HASH);

        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                setUserId(user, USER_ID);
                return user;
            });

        when(jwtTokenProvider.issueTokenPair(USER_ID))
            .thenReturn(
                new TokenPair(
                    ACCESS_TOKEN,
                    REFRESH_TOKEN
                )
            );

        authService.signup(request);

        verify(randomNicknameGenerator, times(2))
            .generate();

        ArgumentCaptor<User> userCaptor =
            ArgumentCaptor.forClass(User.class);

        verify(userRepository)
            .save(userCaptor.capture());

        assertThat(userCaptor.getValue().getNickname())
            .isEqualTo(uniqueNickname);
    }

    @Test
    void 닉네임을_10번_생성해도_모두_중복이면_실패한다() {
        SignupRequest request = new SignupRequest(
            "user@example.com",
            RAW_PASSWORD
        );

        when(
            userRepository.existsByEmailAndDeletedAtIsNull(
                "user@example.com"
            )
        ).thenReturn(false);

        when(randomNicknameGenerator.generate())
            .thenReturn(NICKNAME);

        when(
            userRepository.existsByNicknameAndDeletedAtIsNull(
                NICKNAME
            )
        ).thenReturn(true);

        assertThatThrownBy(
            () -> authService.signup(request)
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException =
                    (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(
                        AuthErrorCode.NICKNAME_GENERATION_FAILED
                    );
            });

        verify(randomNicknameGenerator, times(10))
            .generate();

        verify(userRepository, never())
            .save(any(User.class));
    }

    private void setUserId(User user, Long userId) {
        try {
            Field idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, userId);
        } catch (
            NoSuchFieldException |
            IllegalAccessException exception
        ) {
            throw new IllegalStateException(
                "테스트용 사용자 ID 설정에 실패했습니다.",
                exception
            );
        }
    }
}
