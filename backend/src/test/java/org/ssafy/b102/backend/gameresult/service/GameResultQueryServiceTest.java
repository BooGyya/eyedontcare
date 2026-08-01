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
import java.time.Duration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.ssafy.b102.backend.guest.config.GuestSessionProperties;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.ssafy.b102.backend.game.entity.Game;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.game.repository.GameRepository;
import org.ssafy.b102.backend.game.service.GameService;
import org.ssafy.b102.backend.gameresult.dto.request.ParticipantResultRequest;
import org.ssafy.b102.backend.gameresult.dto.request.SubmitGameResultRequest;
import org.ssafy.b102.backend.gameresult.dto.response.GameResultDetailResponse;
import org.ssafy.b102.backend.gameresult.dto.response.MyGameResultPageResponse;
import org.ssafy.b102.backend.gameresult.entity.GameResult;
import org.ssafy.b102.backend.gameresult.entity.Outcome;
import org.ssafy.b102.backend.gameresult.entity.ParticipantType;
import org.ssafy.b102.backend.gameresult.exception.GameResultErrorCode;
import org.ssafy.b102.backend.gameresult.repository.GameResultRepository;
import org.ssafy.b102.backend.gameresult.repository.ParticipantRepository;
import org.ssafy.b102.backend.global.config.JpaAuditingConfig;
import org.ssafy.b102.backend.global.error.BusinessException;

@DataJpaTest(properties = {
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
	JpaAuditingConfig.class,
	GameResultService.class,
	GameResultQueryService.class,
	GameService.class,
	GameResultQueryServiceTest.GuestConfig.class
})
@EntityScan(basePackageClasses = {GameResult.class, Game.class})
@EnableJpaRepositories(basePackageClasses = {GameResultRepository.class, GameRepository.class})
class GameResultQueryServiceTest {

	private static final String MY_KEY = "USER:1";
	private static final String OPPONENT_KEY = "USER:2";
	private static final Long MY_USER_ID = 1L;
	private static final Instant STARTED_AT = Instant.parse("2026-07-28T09:00:00Z");

	@Autowired
	private GameResultQueryService gameResultQueryService;

	@Autowired
	private GameResultService gameResultService;

	@Autowired
	private GameResultRepository gameResultRepository;

	@Autowired
	private ParticipantRepository participantRepository;

	@Autowired
	private GameRepository gameRepository;

	private Long gameId;

	@BeforeEach
	void setUp() {
		participantRepository.deleteAll();
		gameResultRepository.deleteAll();
		gameRepository.deleteAll();
		gameId = gameRepository.saveAndFlush(Game.of(GameName.HOCKEY, PlayMode.RANDOM, null)).getId();
	}

	// ---------- 내 경기 기록 목록 조회 ----------

	@Test
	void getMyResultsReturnsOnlyMyRecords() {
		submit(MY_KEY, 0);
		submitBetweenOthers();

		MyGameResultPageResponse response = gameResultQueryService.getMyResults(MY_USER_ID, 1, 10);

		assertThat(response.totalElements()).isEqualTo(1);
		assertThat(response.content()).hasSize(1);
		assertThat(response.content().getFirst().gameName()).isEqualTo(GameName.HOCKEY);
		assertThat(response.content().getFirst().myOutcome()).isEqualTo(Outcome.WIN);
	}

	@Test
	void getMyResultsSortsByPlayedAtDescending() {
		submit(MY_KEY, 0);
		submit(MY_KEY, 60);
		submit(MY_KEY, 120);

		MyGameResultPageResponse response = gameResultQueryService.getMyResults(MY_USER_ID, 1, 10);

		assertThat(response.content())
			.extracting(record -> record.playedAt())
			.isSortedAccordingTo((left, right) -> right.compareTo(left));
	}

	@Test
	void getMyResultsAppliesOneBasedPaging() {
		submit(MY_KEY, 0);
		submit(MY_KEY, 60);
		submit(MY_KEY, 120);

		MyGameResultPageResponse firstPage = gameResultQueryService.getMyResults(MY_USER_ID, 1, 2);
		MyGameResultPageResponse secondPage = gameResultQueryService.getMyResults(MY_USER_ID, 2, 2);

		assertThat(firstPage.page()).isEqualTo(1);
		assertThat(firstPage.size()).isEqualTo(2);
		assertThat(firstPage.totalElements()).isEqualTo(3);
		assertThat(firstPage.content()).hasSize(2);

		assertThat(secondPage.page()).isEqualTo(2);
		assertThat(secondPage.content()).hasSize(1);
		assertThat(secondPage.content().getFirst().resultId())
			.isNotIn(firstPage.content().stream().map(record -> record.resultId()).toList());
	}

	@Test
	void getMyResultsReturnsEmptyPageWhenNoRecordExists() {
		MyGameResultPageResponse response = gameResultQueryService.getMyResults(MY_USER_ID, 1, 10);

		assertThat(response.content()).isEmpty();
		assertThat(response.totalElements()).isZero();
	}

	// ---------- 내 경기 기록 상세 조회 ----------

	@Test
	void getResultReturnsDetailWithAllParticipants() {
		Long resultId = submit(MY_KEY, 0);

		GameResultDetailResponse response = gameResultQueryService.getResult(MY_USER_ID, resultId);

		assertThat(response.resultId()).isEqualTo(resultId);
		assertThat(response.gameName()).isEqualTo(GameName.HOCKEY);
		assertThat(response.playMode()).isEqualTo(PlayMode.RANDOM);
		assertThat(response.difficulty()).isNull();
		assertThat(response.startedAt()).isEqualTo(STARTED_AT);
		assertThat(response.participants()).hasSize(2);
		assertThat(response.participants())
			.extracting(participant -> participant.slotNo())
			.containsExactly(1, 2);
	}

	@Test
	void getResultIncludesGameResultJson() {
		Long resultId = submit(MY_KEY, 0);

		GameResultDetailResponse response = gameResultQueryService.getResult(MY_USER_ID, resultId);

		assertThat(response.gameResult()).containsEntry("durationMs", 60000);
	}

	@Test
	void getResultRejectsRequesterWhoDidNotParticipate() {
		Long resultId = submitBetweenOthers();

		assertThatThrownBy(() -> gameResultQueryService.getResult(MY_USER_ID, resultId))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(GameResultErrorCode.RESULT_ACCESS_DENIED));
	}

	@Test
	void getResultRejectsUnknownResultId() {
		assertThatThrownBy(() -> gameResultQueryService.getResult(MY_USER_ID, -1L))
			.isInstanceOf(BusinessException.class)
			.satisfies(thrown -> assertThat(((BusinessException) thrown).getErrorCode())
				.isEqualTo(GameResultErrorCode.RESULT_NOT_FOUND));
	}

	// ---------- fixture ----------

	private Long submit(String winnerKey, int endedAfterSeconds) {
		SubmitGameResultRequest request = new SubmitGameResultRequest(
			UUID.randomUUID(),
			gameId,
			STARTED_AT.plusSeconds(endedAfterSeconds),
			STARTED_AT.plusSeconds(endedAfterSeconds + 60),
			List.of(
				new ParticipantResultRequest(winnerKey, ParticipantType.USER, 1, "A", Outcome.WIN, 1),
				new ParticipantResultRequest(OPPONENT_KEY, ParticipantType.USER, 2, "B", Outcome.LOSE, 2)
			),
			Map.of("durationMs", 60000)
		);

		return gameResultService.submit(winnerKey, request).resultId();
	}

	private Long submitBetweenOthers() {
		SubmitGameResultRequest request = new SubmitGameResultRequest(
			UUID.randomUUID(),
			gameId,
			STARTED_AT,
			STARTED_AT.plusSeconds(60),
			List.of(
				new ParticipantResultRequest(OPPONENT_KEY, ParticipantType.USER, 1, "B", Outcome.WIN, 1),
				new ParticipantResultRequest("USER:3", ParticipantType.USER, 2, "C", Outcome.LOSE, 2)
			),
			Map.of("durationMs", 60000)
		);

		return gameResultService.submit(OPPONENT_KEY, request).resultId();
	}

	/**
	 * 이 테스트는 회원 참가자만 심으므로 게스트 세션은 호출되지 않는다.
	 * {@link GameResultService}가 빈으로 필요할 뿐이라 최소 구성만 제공한다.
	 */
	@TestConfiguration
	static class GuestConfig {

		@Bean
		GuestSessionService guestSessionService() {
			return new GuestSessionService(null, null, new GuestSessionProperties(Duration.ofHours(24)));
		}
	}
}
