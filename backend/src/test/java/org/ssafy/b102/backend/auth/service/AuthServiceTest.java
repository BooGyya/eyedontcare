package org.ssafy.b102.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.ssafy.b102.backend.auth.dto.request.KakaoLoginRequest;
import org.ssafy.b102.backend.auth.dto.request.LoginRequest;
import org.ssafy.b102.backend.auth.dto.request.ReissueRequest;
import org.ssafy.b102.backend.auth.dto.request.SignupRequest;
import org.ssafy.b102.backend.auth.dto.response.TokenResponse;
import org.ssafy.b102.backend.auth.exception.AuthErrorCode;
import org.ssafy.b102.backend.auth.kakao.KakaoOAuthClient;
import org.ssafy.b102.backend.auth.kakao.KakaoUserIdentity;
import org.ssafy.b102.backend.auth.repository.RefreshTokenStore;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.global.security.jwt.TokenPair;
import org.ssafy.b102.backend.user.entity.SocialAccount;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.enums.SocialProvider;
import org.ssafy.b102.backend.user.repository.SocialAccountRepository;
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
    private static final String NEW_ACCESS_TOKEN =
        "new-access-token";
    private static final String NEW_REFRESH_TOKEN =
        "new-refresh-token";

    @Mock
    private UserRepository userRepository;

    @Mock
    private SocialAccountRepository socialAccountRepository;

    @Mock
    private RandomNicknameGenerator randomNicknameGenerator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
            userRepository,
            socialAccountRepository,
            randomNicknameGenerator,
            passwordEncoder,
            jwtTokenProvider,
            refreshTokenStore,
            kakaoOAuthClient
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

        TokenResponse response = authService.signup(request);

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

    @Test
    void 이메일을_정규화하고_로그인하면_토큰을_반환하고_저장한다() {
        LoginRequest request = new LoginRequest(
            " USER@Example.COM ",
            RAW_PASSWORD
        );

        User user = createUser();

        when(
            userRepository.findByEmailAndDeletedAtIsNull(
                "user@example.com"
            )
        ).thenReturn(Optional.of(user));

        when(
            passwordEncoder.matches(
                RAW_PASSWORD,
                PASSWORD_HASH
            )
        ).thenReturn(true);

        when(jwtTokenProvider.issueTokenPair(USER_ID))
            .thenReturn(
                new TokenPair(
                    ACCESS_TOKEN,
                    REFRESH_TOKEN
                )
            );

        TokenResponse response = authService.login(request);

        assertThat(response.accessToken())
            .isEqualTo(ACCESS_TOKEN);

        assertThat(response.refreshToken())
            .isEqualTo(REFRESH_TOKEN);

        verify(refreshTokenStore).save(
            USER_ID,
            REFRESH_TOKEN
        );
    }

    @Test
    void 존재하지_않는_이메일이면_로그인에_실패한다() {
        LoginRequest request = loginRequest();

        when(
            userRepository.findByEmailAndDeletedAtIsNull(
                "user@example.com"
            )
        ).thenReturn(Optional.empty());

        assertInvalidCredentials(request);
        verifyLoginFailureHasNoSideEffects();
    }

    @Test
    void 비밀번호가_일치하지_않으면_로그인에_실패한다() {
        LoginRequest request = loginRequest();
        User user = createUser();

        when(
            userRepository.findByEmailAndDeletedAtIsNull(
                "user@example.com"
            )
        ).thenReturn(Optional.of(user));

        when(
            passwordEncoder.matches(
                RAW_PASSWORD,
                PASSWORD_HASH
            )
        ).thenReturn(false);

        assertInvalidCredentials(request);
        verifyLoginFailureHasNoSideEffects();
    }

    @Test
    void 비밀번호가_null인_소셜_사용자는_로그인에_실패한다() {
        LoginRequest request = loginRequest();
        User user = createUser();
        setUserField(user, "passwordHash", null);

        when(
            userRepository.findByEmailAndDeletedAtIsNull(
                "user@example.com"
            )
        ).thenReturn(Optional.of(user));

        assertInvalidCredentials(request);

        verify(passwordEncoder, never())
            .matches(any(), any());

        verifyLoginFailureHasNoSideEffects();
    }

    @Test
    void 탈퇴한_사용자는_활성_사용자_조회에서_제외되어_로그인에_실패한다() {
        LoginRequest request = loginRequest();

        when(
            userRepository.findByEmailAndDeletedAtIsNull(
                "user@example.com"
            )
        ).thenReturn(Optional.empty());

        assertInvalidCredentials(request);
        verifyLoginFailureHasNoSideEffects();
    }

    @Test
    void 유효한_리프레시_토큰이면_새_토큰을_발급하고_저장한다() {
        ReissueRequest request =
            new ReissueRequest(REFRESH_TOKEN);

        when(
            jwtTokenProvider.parseRefreshTokenUserId(
                REFRESH_TOKEN
            )
        ).thenReturn(Optional.of(USER_ID));

        when(
            userRepository.existsByIdAndDeletedAtIsNull(USER_ID)
        ).thenReturn(true);

        when(refreshTokenStore.findByUserId(USER_ID))
            .thenReturn(Optional.of(REFRESH_TOKEN));

        when(jwtTokenProvider.issueTokenPair(USER_ID))
            .thenReturn(
                new TokenPair(
                    NEW_ACCESS_TOKEN,
                    NEW_REFRESH_TOKEN
                )
            );

        TokenResponse response = authService.reissue(request);

        assertThat(response.accessToken())
            .isEqualTo(NEW_ACCESS_TOKEN);
        assertThat(response.refreshToken())
            .isEqualTo(NEW_REFRESH_TOKEN);

        verify(refreshTokenStore).save(
            USER_ID,
            response.refreshToken()
        );
    }

    @Test
    void 저장된_토큰과_요청_토큰이_다르면_재발급에_실패한다() {
        mockValidRefreshTokenParsing();

        when(refreshTokenStore.findByUserId(USER_ID))
            .thenReturn(Optional.of("different-token"));

        assertInvalidRefreshToken(
            new ReissueRequest(REFRESH_TOKEN)
        );
        verifyReissueFailureHasNoSideEffects();
    }

    @Test
    void 저장된_리프레시_토큰이_없으면_재발급에_실패한다() {
        mockValidRefreshTokenParsing();

        when(refreshTokenStore.findByUserId(USER_ID))
            .thenReturn(Optional.empty());

        assertInvalidRefreshToken(
            new ReissueRequest(REFRESH_TOKEN)
        );
        verifyReissueFailureHasNoSideEffects();
    }

    @Test
    void 존재하지_않거나_탈퇴한_회원이면_재발급에_실패한다() {
        when(
            jwtTokenProvider.parseRefreshTokenUserId(
                REFRESH_TOKEN
            )
        ).thenReturn(Optional.of(USER_ID));

        when(
            userRepository.existsByIdAndDeletedAtIsNull(USER_ID)
        ).thenReturn(false);

        assertInvalidRefreshToken(
            new ReissueRequest(REFRESH_TOKEN)
        );

        verify(refreshTokenStore, never())
            .findByUserId(any());
        verifyReissueFailureHasNoSideEffects();
    }

    @Test
    void 유효하지_않은_리프레시_토큰이면_재발급에_실패한다() {
        when(
            jwtTokenProvider.parseRefreshTokenUserId(
                REFRESH_TOKEN
            )
        ).thenReturn(Optional.empty());

        assertInvalidRefreshToken(
            new ReissueRequest(REFRESH_TOKEN)
        );

        verify(userRepository, never())
            .existsByIdAndDeletedAtIsNull(any());
        verify(refreshTokenStore, never())
            .findByUserId(any());
        verifyReissueFailureHasNoSideEffects();
    }

    @Test
    void 회전된_이전_리프레시_토큰은_재사용할_수_없다() {
        ReissueRequest request =
            new ReissueRequest(REFRESH_TOKEN);

        when(
            jwtTokenProvider.parseRefreshTokenUserId(
                REFRESH_TOKEN
            )
        ).thenReturn(Optional.of(USER_ID));

        when(
            userRepository.existsByIdAndDeletedAtIsNull(USER_ID)
        ).thenReturn(true);

        when(refreshTokenStore.findByUserId(USER_ID))
            .thenReturn(
                Optional.of(REFRESH_TOKEN),
                Optional.of(NEW_REFRESH_TOKEN)
            );

        when(jwtTokenProvider.issueTokenPair(USER_ID))
            .thenReturn(
                new TokenPair(
                    NEW_ACCESS_TOKEN,
                    NEW_REFRESH_TOKEN
                )
            );

        TokenResponse response = authService.reissue(request);

        assertThat(response.refreshToken())
            .isEqualTo(NEW_REFRESH_TOKEN);

        assertInvalidRefreshToken(request);

        verify(jwtTokenProvider, times(1))
            .issueTokenPair(USER_ID);
        verify(refreshTokenStore, times(1)).save(
            USER_ID,
            NEW_REFRESH_TOKEN
        );
    }

    @Test
    void 로그아웃하면_인증된_사용자의_리프레시_토큰을_삭제한다() {
        authService.logout(USER_ID);

        verify(refreshTokenStore).deleteByUserId(USER_ID);
        verify(refreshTokenStore, never()).findByUserId(any());
        verify(refreshTokenStore, never()).save(any(), any());
        verifyNoInteractions(
            userRepository,
            randomNicknameGenerator,
            passwordEncoder,
            jwtTokenProvider
        );
    }

    @Test
    void 리프레시_토큰이_없어도_반복_로그아웃에_성공한다() {
        authService.logout(USER_ID);
        authService.logout(USER_ID);

        verify(refreshTokenStore, times(2))
            .deleteByUserId(USER_ID);
    }

    @Test
    void 로그아웃으로_삭제된_리프레시_토큰은_재발급할_수_없다() {
        authService.logout(USER_ID);

        when(
            jwtTokenProvider.parseRefreshTokenUserId(
                REFRESH_TOKEN
            )
        ).thenReturn(Optional.of(USER_ID));
        when(
            userRepository.existsByIdAndDeletedAtIsNull(USER_ID)
        ).thenReturn(true);
        when(refreshTokenStore.findByUserId(USER_ID))
            .thenReturn(Optional.empty());

        assertInvalidRefreshToken(
            new ReissueRequest(REFRESH_TOKEN)
        );

        verify(refreshTokenStore).deleteByUserId(USER_ID);
        verify(refreshTokenStore).findByUserId(USER_ID);
        verify(jwtTokenProvider, never())
            .issueTokenPair(any());
        verify(refreshTokenStore, never())
            .save(any(), any());
    }

    @Test
    void 활성_회원은_개인정보를_익명화하고_리프레시_토큰을_삭제한다() {
        User user = createUser();
        Instant beforeWithdrawal = Instant.now();

        when(
            userRepository.findByIdAndDeletedAtIsNull(USER_ID)
        ).thenReturn(Optional.of(user));

        authService.withdraw(USER_ID);

        assertThat(user.getDeletedAt())
            .isAfterOrEqualTo(beforeWithdrawal);
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getNickname())
            .isEqualTo("withdrawn-" + USER_ID);

        verify(userRepository)
            .findByIdAndDeletedAtIsNull(USER_ID);
        verify(refreshTokenStore).deleteByUserId(USER_ID);
        verify(userRepository, never()).save(any());
    }

    @Test
    void 활성_회원이_없으면_AUTH_005로_탈퇴에_실패한다() {
        when(
            userRepository.findByIdAndDeletedAtIsNull(USER_ID)
        ).thenReturn(Optional.empty());

        assertThatThrownBy(
            () -> authService.withdraw(USER_ID)
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException =
                    (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(AuthErrorCode.USER_NOT_FOUND);
            });

        verify(refreshTokenStore, never())
            .deleteByUserId(any());
    }

    @Test
    void Redis_삭제_실패는_숨기지_않아_트랜잭션_rollback을_허용한다() {
        User user = createUser();
        RuntimeException redisFailure =
            new RuntimeException("redis unavailable");

        when(
            userRepository.findByIdAndDeletedAtIsNull(USER_ID)
        ).thenReturn(Optional.of(user));
        doThrow(redisFailure)
            .when(refreshTokenStore)
            .deleteByUserId(USER_ID);

        assertThatThrownBy(
            () -> authService.withdraw(USER_ID)
        ).isSameAs(redisFailure);

        verify(refreshTokenStore).deleteByUserId(USER_ID);
    }

    @Test
    void 기존_카카오_회원은_새_User를_생성하지_않고_로그인한다() {
        KakaoLoginRequest request =
            new KakaoLoginRequest("authorization-code");
        User user = createUser();
        SocialAccount socialAccount = SocialAccount.create(
            user,
            SocialProvider.KAKAO,
            "12345"
        );

        when(
            kakaoOAuthClient.authenticate(
                request.authorizationCode()
            )
        ).thenReturn(new KakaoUserIdentity("12345"));
        when(
            socialAccountRepository.findActiveAccount(
                SocialProvider.KAKAO,
                "12345"
            )
        ).thenReturn(Optional.of(socialAccount));
        when(jwtTokenProvider.issueTokenPair(USER_ID))
            .thenReturn(
                new TokenPair(ACCESS_TOKEN, REFRESH_TOKEN)
            );

        TokenResponse response =
            authService.loginWithKakao(request);

        assertThat(response.accessToken())
            .isEqualTo(ACCESS_TOKEN);
        assertThat(response.refreshToken())
            .isEqualTo(REFRESH_TOKEN);
        verify(userRepository, never()).save(any());
        verify(socialAccountRepository, never())
            .saveAndFlush(any());
        verify(refreshTokenStore)
            .save(USER_ID, REFRESH_TOKEN);
    }

    @Test
    void 신규_카카오_회원은_User와_SocialAccount를_생성한다() {
        KakaoLoginRequest request =
            new KakaoLoginRequest("authorization-code");

        when(
            kakaoOAuthClient.authenticate(
                request.authorizationCode()
            )
        ).thenReturn(new KakaoUserIdentity("12345"));
        when(
            socialAccountRepository.findActiveAccount(
                SocialProvider.KAKAO,
                "12345"
            )
        ).thenReturn(Optional.empty());
        when(randomNicknameGenerator.generate())
            .thenReturn(NICKNAME);
        when(
            userRepository.existsByNicknameAndDeletedAtIsNull(
                NICKNAME
            )
        ).thenReturn(false);
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                setUserId(user, USER_ID);
                return user;
            });
        when(
            socialAccountRepository.saveAndFlush(
                any(SocialAccount.class)
            )
        ).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenProvider.issueTokenPair(USER_ID))
            .thenReturn(
                new TokenPair(ACCESS_TOKEN, REFRESH_TOKEN)
            );

        authService.loginWithKakao(request);

        ArgumentCaptor<User> userCaptor =
            ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isNull();
        assertThat(savedUser.getPasswordHash()).isNull();
        assertThat(savedUser.getNickname()).isEqualTo(NICKNAME);

        ArgumentCaptor<SocialAccount> accountCaptor =
            ArgumentCaptor.forClass(SocialAccount.class);
        verify(socialAccountRepository)
            .saveAndFlush(accountCaptor.capture());

        SocialAccount savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getUser()).isSameAs(savedUser);
        assertThat(savedAccount.getProvider())
            .isEqualTo(SocialProvider.KAKAO);
        assertThat(savedAccount.getProviderUserId())
            .isEqualTo("12345");
    }

    @Test
    void 안전하게_식별한_소셜_계정_UNIQUE_충돌만_AUTH_008이다() {
        mockNewKakaoUser();

        DataIntegrityViolationException conflict =
            constraintViolation(
                "uk_social_accounts_provider_identity"
            );

        when(
            socialAccountRepository.saveAndFlush(
                any(SocialAccount.class)
            )
        ).thenThrow(conflict);

        assertThatThrownBy(
            () -> authService.loginWithKakao(
                new KakaoLoginRequest("authorization-code")
            )
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> assertThat(
                ((BusinessException) exception).getErrorCode()
            ).isEqualTo(AuthErrorCode.SOCIAL_ACCOUNT_CONFLICT));

        verify(jwtTokenProvider, never())
            .issueTokenPair(any());
        verify(refreshTokenStore, never()).save(any(), any());
    }

    @Test
    void 다른_DB_제약_오류는_AUTH_008로_변환하지_않는다() {
        mockNewKakaoUser();

        DataIntegrityViolationException unexpected =
            constraintViolation("uk_users_nickname");

        when(
            socialAccountRepository.saveAndFlush(
                any(SocialAccount.class)
            )
        ).thenThrow(unexpected);

        assertThatThrownBy(
            () -> authService.loginWithKakao(
                new KakaoLoginRequest("authorization-code")
            )
        ).isSameAs(unexpected);
    }

    @Test
    void 탈퇴할_때_연결된_SocialAccount를_명시적으로_삭제한다() {
        User user = createUser();
        when(
            userRepository.findByIdAndDeletedAtIsNull(USER_ID)
        ).thenReturn(Optional.of(user));

        authService.withdraw(USER_ID);

        verify(socialAccountRepository).deleteByUserId(USER_ID);
        verify(refreshTokenStore).deleteByUserId(USER_ID);
    }

    private LoginRequest loginRequest() {
        return new LoginRequest(
            "user@example.com",
            RAW_PASSWORD
        );
    }

    private void mockNewKakaoUser() {
        when(
            kakaoOAuthClient.authenticate("authorization-code")
        ).thenReturn(new KakaoUserIdentity("12345"));
        when(
            socialAccountRepository.findActiveAccount(
                SocialProvider.KAKAO,
                "12345"
            )
        ).thenReturn(Optional.empty());
        when(randomNicknameGenerator.generate())
            .thenReturn(NICKNAME);
        when(
            userRepository.existsByNicknameAndDeletedAtIsNull(
                NICKNAME
            )
        ).thenReturn(false);
        when(userRepository.save(any(User.class)))
            .thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                setUserId(user, USER_ID);
                return user;
            });
    }

    private DataIntegrityViolationException constraintViolation(
        String constraintName
    ) {
        return new DataIntegrityViolationException(
            "database constraint violation",
            new ConstraintViolationException(
                "constraint violation",
                new SQLException(),
                constraintName
            )
        );
    }

    private void mockValidRefreshTokenParsing() {
        when(
            jwtTokenProvider.parseRefreshTokenUserId(
                REFRESH_TOKEN
            )
        ).thenReturn(Optional.of(USER_ID));

        when(
            userRepository.existsByIdAndDeletedAtIsNull(USER_ID)
        ).thenReturn(true);
    }

    private void assertInvalidRefreshToken(
        ReissueRequest request
    ) {
        assertThatThrownBy(
            () -> authService.reissue(request)
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException =
                    (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(
                        AuthErrorCode.INVALID_REFRESH_TOKEN
                    );
            });
    }

    private void verifyReissueFailureHasNoSideEffects() {
        verify(jwtTokenProvider, never())
            .issueTokenPair(any());

        verify(refreshTokenStore, never())
            .save(any(), any());
    }

    private User createUser() {
        User user = User.createLocal(
            "user@example.com",
            PASSWORD_HASH,
            NICKNAME
        );

        setUserId(user, USER_ID);
        return user;
    }

    private void assertInvalidCredentials(LoginRequest request) {
        assertThatThrownBy(
            () -> authService.login(request)
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> {
                BusinessException businessException =
                    (BusinessException) exception;

                assertThat(businessException.getErrorCode())
                    .isEqualTo(
                        AuthErrorCode.INVALID_CREDENTIALS
                    );
            });
    }

    private void verifyLoginFailureHasNoSideEffects() {
        verify(jwtTokenProvider, never())
            .issueTokenPair(any());

        verify(refreshTokenStore, never())
            .save(any(), any());
    }

    private void setUserId(User user, Long userId) {
        setUserField(user, "id", userId);
    }

    private void setUserField(
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
            throw new IllegalStateException(
                "테스트용 사용자 ID 설정에 실패했습니다.",
                exception
            );
        }
    }
}
