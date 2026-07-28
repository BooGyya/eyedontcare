package org.ssafy.b102.backend.gameresult.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.ssafy.b102.backend.game.entity.Game;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.game.repository.GameRepository;
import org.ssafy.b102.backend.game.service.GameService;
import org.ssafy.b102.backend.gameresult.dto.request.ParticipantResultRequest;
import org.ssafy.b102.backend.gameresult.dto.request.SubmitGameResultRequest;
import org.ssafy.b102.backend.gameresult.dto.response.SubmitGameResultResponse;
import org.ssafy.b102.backend.gameresult.entity.GameResult;
import org.ssafy.b102.backend.gameresult.entity.Outcome;
import org.ssafy.b102.backend.gameresult.entity.ParticipantType;
import org.ssafy.b102.backend.gameresult.exception.GameResultErrorCode;
import org.ssafy.b102.backend.gameresult.repository.GameResultRepository;
import org.ssafy.b102.backend.global.config.JpaAuditingConfig;
import org.ssafy.b102.backend.global.error.BusinessException;

@DataJpaTest(properties = {
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAuditingConfig.class, GameResultService.class, GameService.class})
@EntityScan(basePackageClasses = {GameResult.class, Game.class})
@EnableJpaRepositories(basePackageClasses = {GameResultRepository.class, GameRepository.class})
class GameResultServiceTest {

	private static final String REQUESTER_KEY = "USER:1";
	private static final String OPPONENT_KEY = "USER:2";
	private static final Instant STARTED_AT = Instant.parse("2026-07-28T09:00:00Z");
	private static final Instant ENDED_AT = Instant.parse("2026-07-28T09:01:00Z");

	@Autowired
	private GameResultService gameResultService;

	@Autowired
	private GameResultRepository gameResultRepository;

	@Autowired
	private GameRepository gameRepository;

	private Long gameId;

	@BeforeEach
	void setUp() {
		gameResultRepository.deleteAll();
		gameRepository.deleteAll();
		gameId = gameRepository.saveAndFlush(Game.of(GameName.HOCKEY, PlayMode.RANDOM, null)).getId();
	}

	@Test
	void submitStoresResultWithParticipants() {
		SubmitGameResultRequest request = twoPlayerRequest(UUID.randomUUID());

		SubmitGameResultResponse response = gameResultService.submit(REQUESTER_KEY, request);

		assertThat(response.resultId()).isNotNull();

		GameResult saved = gameResultRepository.findById(response.resultId()).orElseThrow();
		assertThat(saved.getGame().getId()).isEqualTo(gameId);
		assertThat(saved.getStartedAt()).isEqualTo(STARTED_AT);
		assertThat(saved.getEndedAt()).isEqualTo(ENDED_AT);
		assertThat(saved.getParticipants()).hasSize(2);
		assertThat(saved.getParticipants())
			.extracting(participant -> participant.getOutcome())
			.containsExactlyInAnyOrder(Outcome.WIN, Outcome.LOSE);
	}

	@Test
	void submitStoresGameResultJsonAsGiven() {
		SubmitGameResultRequest request = twoPlayerRequest(UUID.randomUUID());

		SubmitGameResultResponse response = gameResultService.submit(REQUESTER_KEY, request);

		GameResult saved = gameResultRepository.findById(response.resultId()).orElseThrow();
		assertThat(saved.getGameResult()).containsEntry("durationMs", 60000);
	}

	@Test
	void submitDerivesUserIdFromParticipantKey() {
		SubmitGameResultResponse response =
			gameResultService.submit(REQUESTER_KEY, twoPlayerRequest(UUID.randomUUID()));

		GameResult saved = gameResultRepository.findById(response.resultId()).orElseThrow();
		assertThat(saved.getParticipants())
			.filteredOn(participant -> participant.getSlotNo() == 1)
			.singleElement()
			.satisfies(user -> assertThat(user.getUserId()).isEqualTo(1L));
	}

	@Test
	void submitStoresGuestParticipantWithoutUserId() {
		SubmitGameResultRequest request = requestOf(
			UUID.randomUUID(),
			gameId,
			new ParticipantResultRequest(REQUESTER_KEY, ParticipantType.USER, 1, "회원", Outcome.WIN, 1),
			new ParticipantResultRequest("GUEST:abc", ParticipantType.GUEST, 2, "게스트", Outcome.LOSE, 2)
		);

		SubmitGameResultResponse response = gameResultService.submit(REQUESTER_KEY, request);

		GameResult saved = gameResultRepository.findById(response.resultId()).orElseThrow();
		assertThat(saved.getParticipants())
			.filteredOn(participant -> participant.getParticipantType() == ParticipantType.GUEST)
			.singleElement()
			.satisfies(guest -> {
				assertThat(guest.getUserId()).isNull();
				assertThat(guest.getDisplayName()).isEqualTo("게스트");
			});
	}

	@Test
	void submitRejectsDuplicatePlayId() {
		UUID playId = UUID.randomUUID();
		gameResultService.submit(REQUESTER_KEY, twoPlayerRequest(playId));

		assertThatThrownBy(() -> gameResultService.submit(REQUESTER_KEY, twoPlayerRequest(playId)))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(GameResultErrorCode.DUPLICATE_RESULT));
	}

	@Test
	void submitRejectsUnknownGameId() {
		SubmitGameResultRequest request = requestOf(
			UUID.randomUUID(),
			-1L,
			new ParticipantResultRequest(REQUESTER_KEY, ParticipantType.USER, 1, "회원", Outcome.WIN, 1)
		);

		assertThatThrownBy(() -> gameResultService.submit(REQUESTER_KEY, request))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(GameResultErrorCode.GAME_NOT_FOUND));
	}

	@Test
	void submitRejectsDuplicatedSlotNo() {
		SubmitGameResultRequest request = requestOf(
			UUID.randomUUID(),
			gameId,
			new ParticipantResultRequest(REQUESTER_KEY, ParticipantType.USER, 1, "A", Outcome.WIN, 1),
			new ParticipantResultRequest(OPPONENT_KEY, ParticipantType.USER, 1, "B", Outcome.LOSE, 2)
		);

		assertThatThrownBy(() -> gameResultService.submit(REQUESTER_KEY, request))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(GameResultErrorCode.INVALID_PARTICIPANTS));
	}

	@Test
	void submitRejectsRankOutOfRange() {
		SubmitGameResultRequest request = requestOf(
			UUID.randomUUID(),
			gameId,
			new ParticipantResultRequest(REQUESTER_KEY, ParticipantType.USER, 1, "A", Outcome.WIN, 1),
			new ParticipantResultRequest(OPPONENT_KEY, ParticipantType.USER, 2, "B", Outcome.LOSE, 3)
		);

		assertThatThrownBy(() -> gameResultService.submit(REQUESTER_KEY, request))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(GameResultErrorCode.INVALID_PARTICIPANTS));
	}

	@Test
	void submitRejectsRequesterNotInParticipants() {
		SubmitGameResultRequest request = requestOf(
			UUID.randomUUID(),
			gameId,
			new ParticipantResultRequest(OPPONENT_KEY, ParticipantType.USER, 1, "B", Outcome.WIN, 1)
		);

		assertThatThrownBy(() -> gameResultService.submit(REQUESTER_KEY, request))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(GameResultErrorCode.REQUESTER_NOT_PARTICIPANT));
	}

	@Test
	void submitRejectsEndedAtBeforeStartedAt() {
		SubmitGameResultRequest request = new SubmitGameResultRequest(
			UUID.randomUUID(),
			gameId,
			ENDED_AT,
			STARTED_AT,
			List.of(new ParticipantResultRequest(REQUESTER_KEY, ParticipantType.USER, 1, "A", Outcome.WIN, 1)),
			Map.of("durationMs", 60000)
		);

		assertThatThrownBy(() -> gameResultService.submit(REQUESTER_KEY, request))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(GameResultErrorCode.INVALID_PLAY_PERIOD));
	}

	@Test
	void submitAcceptsForfeitOutcome() {
		SubmitGameResultRequest request = requestOf(
			UUID.randomUUID(),
			gameId,
			new ParticipantResultRequest(REQUESTER_KEY, ParticipantType.USER, 1, "A", Outcome.FORFEIT, 2),
			new ParticipantResultRequest(OPPONENT_KEY, ParticipantType.USER, 2, "B", Outcome.WIN, 1)
		);

		SubmitGameResultResponse response = gameResultService.submit(REQUESTER_KEY, request);

		assertThat(response.resultId()).isNotNull();
	}

	private SubmitGameResultRequest twoPlayerRequest(UUID playId) {
		return requestOf(
			playId,
			gameId,
			new ParticipantResultRequest(REQUESTER_KEY, ParticipantType.USER, 1, "A", Outcome.WIN, 1),
			new ParticipantResultRequest(OPPONENT_KEY, ParticipantType.USER, 2, "B", Outcome.LOSE, 2)
		);
	}

	private SubmitGameResultRequest requestOf(
		UUID playId,
		Long targetGameId,
		ParticipantResultRequest... participants
	) {
		return new SubmitGameResultRequest(
			playId,
			targetGameId,
			STARTED_AT,
			ENDED_AT,
			List.of(participants),
			Map.of("durationMs", 60000)
		);
	}
}
