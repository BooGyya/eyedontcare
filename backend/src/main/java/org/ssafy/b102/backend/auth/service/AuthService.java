package org.ssafy.b102.backend.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Service
public class AuthService {

    private static final int MAX_NICKNAME_GENERATION_ATTEMPTS = 10;

    private final UserRepository userRepository;
    private final RandomNicknameGenerator randomNicknameGenerator;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    public AuthService(
        UserRepository userRepository,
        RandomNicknameGenerator randomNicknameGenerator,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider jwtTokenProvider,
        RefreshTokenStore refreshTokenStore
    ) {
        this.userRepository = userRepository;
        this.randomNicknameGenerator = randomNicknameGenerator;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenStore = refreshTokenStore;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
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

        TokenPair tokenPair =
            jwtTokenProvider.issueTokenPair(savedUser.getId());

        refreshTokenStore.save(
            savedUser.getId(),
            tokenPair.refreshToken()
        );

        return SignupResponse.from(tokenPair);
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
