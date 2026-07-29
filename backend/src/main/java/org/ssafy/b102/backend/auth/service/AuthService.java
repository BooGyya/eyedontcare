package org.ssafy.b102.backend.auth.service;

import java.time.Instant;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Service
public class AuthService {

    private static final int MAX_NICKNAME_GENERATION_ATTEMPTS = 10;
    private static final String SOCIAL_ACCOUNT_USER_CONSTRAINT =
        "uk_social_accounts_user";
    private static final String SOCIAL_ACCOUNT_IDENTITY_CONSTRAINT =
        "uk_social_accounts_provider_identity";

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final RandomNicknameGenerator randomNicknameGenerator;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final KakaoOAuthClient kakaoOAuthClient;

    public AuthService(
        UserRepository userRepository,
        SocialAccountRepository socialAccountRepository,
        RandomNicknameGenerator randomNicknameGenerator,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider jwtTokenProvider,
        RefreshTokenStore refreshTokenStore,
        KakaoOAuthClient kakaoOAuthClient
    ) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
        this.randomNicknameGenerator = randomNicknameGenerator;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.kakaoOAuthClient = kakaoOAuthClient;
    }

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());

        validateEmailNotDuplicated(email);

        String nickname = generateUniqueNickname();
        String passwordHash = passwordEncoder.encode(request.password());

        User user = User.createLocal(
            email,
            passwordHash,
            nickname
        );

        User savedUser = userRepository.save(user);

        return issueAndStoreTokens(savedUser);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());

        User user = userRepository
            .findByEmailAndDeletedAtIsNull(email)
            .orElseThrow(this::invalidCredentials);

        String passwordHash = user.getPasswordHash();

        if (
            passwordHash == null ||
            !passwordEncoder.matches(
                request.password(),
                passwordHash
            )
        ) {
            throw invalidCredentials();
        }

        return issueAndStoreTokens(user);
    }

    @Transactional
    public TokenResponse loginWithKakao(
        KakaoLoginRequest request
    ) {
        KakaoUserIdentity identity =
            kakaoOAuthClient.authenticate(
                request.authorizationCode()
            );

        User user = socialAccountRepository
            .findActiveAccount(
                SocialProvider.KAKAO,
                identity.providerUserId()
            )
            .map(SocialAccount::getUser)
            .orElseGet(() -> createKakaoUser(identity));

        return issueAndStoreTokens(user);
    }

    public TokenResponse reissue(ReissueRequest request) {
        String refreshToken = request.refreshToken();

        Long userId = jwtTokenProvider
            .parseRefreshTokenUserId(refreshToken)
            .orElseThrow(this::invalidRefreshToken);

        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw invalidRefreshToken();
        }

        String storedRefreshToken = refreshTokenStore
            .findByUserId(userId)
            .orElseThrow(this::invalidRefreshToken);

        if (!storedRefreshToken.equals(refreshToken)) {
            throw invalidRefreshToken();
        }

        TokenPair tokenPair =
            jwtTokenProvider.issueTokenPair(userId);

        refreshTokenStore.save(
            userId,
            tokenPair.refreshToken()
        );

        return TokenResponse.from(tokenPair);
    }

    public void logout(Long userId) {
        refreshTokenStore.deleteByUserId(userId);
    }

    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository
            .findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(
                () -> new BusinessException(
                    AuthErrorCode.USER_NOT_FOUND
                )
            );

        socialAccountRepository.deleteByUserId(userId);
        user.withdraw(Instant.now());
        refreshTokenStore.deleteByUserId(userId);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private void validateEmailNotDuplicated(String email) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(email)) {
            throw new BusinessException(
                AuthErrorCode.EMAIL_ALREADY_EXISTS
            );
        }
    }

    private TokenResponse issueAndStoreTokens(User user) {
        TokenPair tokenPair =
            jwtTokenProvider.issueTokenPair(user.getId());

        refreshTokenStore.save(
            user.getId(),
            tokenPair.refreshToken()
        );

        return TokenResponse.from(tokenPair);
    }

    private User createKakaoUser(KakaoUserIdentity identity) {
        User user = User.createSocial(
            generateUniqueNickname()
        );

        User savedUser = userRepository.save(user);
        SocialAccount socialAccount = SocialAccount.create(
            savedUser,
            SocialProvider.KAKAO,
            identity.providerUserId()
        );

        try {
            socialAccountRepository.saveAndFlush(
                socialAccount
            );
        } catch (DataIntegrityViolationException exception) {
            if (isSocialAccountUniqueConflict(exception)) {
                throw new BusinessException(
                    AuthErrorCode.SOCIAL_ACCOUNT_CONFLICT
                );
            }

            throw exception;
        }

        return savedUser;
    }

    private boolean isSocialAccountUniqueConflict(
        DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (
                cause instanceof ConstraintViolationException
                    constraintViolation
            ) {
                String constraintName =
                    constraintViolation.getConstraintName();

                return SOCIAL_ACCOUNT_USER_CONSTRAINT.equals(
                    constraintName
                ) ||
                    SOCIAL_ACCOUNT_IDENTITY_CONSTRAINT.equals(
                        constraintName
                    );
            }

            cause = cause.getCause();
        }

        return false;
    }

    private BusinessException invalidCredentials() {
        return new BusinessException(
            AuthErrorCode.INVALID_CREDENTIALS
        );
    }

    private BusinessException invalidRefreshToken() {
        return new BusinessException(
            AuthErrorCode.INVALID_REFRESH_TOKEN
        );
    }

    private String generateUniqueNickname() {
        for (
            int attempt = 0;
            attempt < MAX_NICKNAME_GENERATION_ATTEMPTS;
            attempt++
        ) {
            String nickname = randomNicknameGenerator.generate();

            boolean alreadyExists =
                userRepository.existsByNicknameAndDeletedAtIsNull(
                    nickname
                );

            if (!alreadyExists) {
                return nickname;
            }
        }

        throw new BusinessException(
            AuthErrorCode.NICKNAME_GENERATION_FAILED
        );
    }
}
