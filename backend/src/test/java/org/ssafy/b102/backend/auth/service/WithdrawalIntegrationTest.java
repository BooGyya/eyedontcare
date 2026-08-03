package org.ssafy.b102.backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.b102.backend.auth.dto.request.LoginRequest;
import org.ssafy.b102.backend.auth.dto.request.ReissueRequest;
import org.ssafy.b102.backend.auth.dto.request.SignupRequest;
import org.ssafy.b102.backend.auth.dto.response.TokenResponse;
import org.ssafy.b102.backend.auth.exception.AuthErrorCode;
import org.ssafy.b102.backend.auth.repository.RefreshTokenStore;
import org.ssafy.b102.backend.game.entity.Game;
import org.ssafy.b102.backend.game.repository.GameRepository;
import org.ssafy.b102.backend.gameresult.entity.GameResult;
import org.ssafy.b102.backend.gameresult.entity.Outcome;
import org.ssafy.b102.backend.gameresult.entity.Participant;
import org.ssafy.b102.backend.gameresult.entity.ParticipantType;
import org.ssafy.b102.backend.gameresult.repository.GameResultRepository;
import org.ssafy.b102.backend.gameresult.repository.ParticipantRepository;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.enums.ProfileImageCode;
import org.ssafy.b102.backend.user.repository.UserRepository;

@SpringBootTest
@Transactional
class WithdrawalIntegrationTest {

    private static final String PASSWORD = "password123";

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenStore refreshTokenStore;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private GameResultRepository gameResultRepository;

    @Autowired
    private ParticipantRepository participantRepository;

    private Long withdrawnUserId;
    private Long newUserId;

    @AfterEach
    void deleteRefreshTokens() {
        if (withdrawnUserId != null) {
            refreshTokenStore.deleteByUserId(withdrawnUserId);
        }
        if (newUserId != null) {
            refreshTokenStore.deleteByUserId(newUserId);
        }
    }

    @Test
    void 탈퇴_후_동일_이메일로_새_계정을_만들고_경기_기록을_보존한다() {
        String email =
            "withdrawal-" + UUID.randomUUID() + "@example.com";
        SignupRequest signupRequest =
            new SignupRequest(email, PASSWORD);

        TokenResponse oldTokens =
            authService.signup(signupRequest);

        User withdrawnUser = userRepository
            .findByEmailAndDeletedAtIsNull(email)
            .orElseThrow();
        withdrawnUserId = withdrawnUser.getId();

        Participant participant =
            saveGameResult(withdrawnUserId);

        authService.withdraw(withdrawnUserId);
        userRepository.flush();

        assertThat(withdrawnUser.getDeletedAt()).isNotNull();
        assertThat(withdrawnUser.getEmail()).isNull();
        assertThat(withdrawnUser.getPasswordHash()).isNull();
        assertThat(withdrawnUser.getNickname())
            .isEqualTo("withdrawn-" + withdrawnUserId);
        assertThat(withdrawnUser.getProfileImageCode())
            .isEqualTo(ProfileImageCode.PROFILE_1);
        assertThat(
            refreshTokenStore.findByUserId(withdrawnUserId)
        ).isEmpty();

        assertThatThrownBy(
            () -> authService.reissue(
                new ReissueRequest(oldTokens.refreshToken())
            )
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> assertThat(
                ((BusinessException) exception).getErrorCode()
            ).isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));

        assertThatThrownBy(
            () -> authService.login(
                new LoginRequest(email, PASSWORD)
            )
        )
            .isInstanceOf(BusinessException.class)
            .satisfies(exception -> assertThat(
                ((BusinessException) exception).getErrorCode()
            ).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS));

        authService.signup(signupRequest);

        User newUser = userRepository
            .findByEmailAndDeletedAtIsNull(email)
            .orElseThrow();
        newUserId = newUser.getId();
        userRepository.flush();

        assertThat(newUserId)
            .isNotEqualTo(withdrawnUserId);
        assertThat(
            userRepository.findAll().stream()
                .map(User::getId)
                .filter(id ->
                    id.equals(withdrawnUserId) ||
                    id.equals(newUserId)
                )
        ).containsExactlyInAnyOrder(
            withdrawnUserId,
            newUserId
        );

        Participant savedParticipant = participantRepository
            .findById(participant.getId())
            .orElseThrow();

        assertThat(
            gameResultRepository.existsById(
                savedParticipant.getGameResult().getId()
            )
        ).isTrue();
        assertThat(savedParticipant.getUserId())
            .isEqualTo(withdrawnUserId);
        assertThat(savedParticipant.getDisplayName())
            .isEqualTo("original-display-name");
    }

    private Participant saveGameResult(Long userId) {
        Game game = gameRepository.findAll()
            .stream()
            .findFirst()
            .orElseThrow();

        GameResult gameResult = GameResult.of(
            UUID.randomUUID(),
            game,
            Map.of("durationMs", 60_000),
            Instant.parse("2026-07-30T00:00:00Z"),
            Instant.parse("2026-07-30T00:01:00Z")
        );

        Participant participant = Participant.of(
            userId,
            ParticipantType.USER,
            1,
            Outcome.WIN,
            1,
            "original-display-name",
            null
        );

        gameResult.addParticipant(participant);
        gameResultRepository.saveAndFlush(gameResult);

        return participant;
    }
}
