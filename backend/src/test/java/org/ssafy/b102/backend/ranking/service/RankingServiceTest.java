package org.ssafy.b102.backend.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.gameresult.entity.GameResult;
import org.ssafy.b102.backend.gameresult.entity.Outcome;
import org.ssafy.b102.backend.gameresult.entity.Participant;
import org.ssafy.b102.backend.gameresult.entity.ParticipantType;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.ranking.dto.response.GameRankingResponse;
import org.ssafy.b102.backend.ranking.dto.response.RankingEntry;
import org.ssafy.b102.backend.ranking.dto.response.RankingListResponse;
import org.ssafy.b102.backend.ranking.exception.RankingErrorCode;
import org.ssafy.b102.backend.ranking.repository.RankingRepository;
import org.ssafy.b102.backend.ranking.support.RankType;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");
	private static final Instant T1 = Instant.parse("2026-08-05T01:00:00Z");
	private static final Instant T2 = Instant.parse("2026-08-05T02:00:00Z");
	private static final Instant T3 = Instant.parse("2026-08-05T03:00:00Z");
	private static final Instant T4 = Instant.parse("2026-08-05T04:00:00Z");

	@Mock
	private RankingRepository rankingRepository;

	@Mock
	private UserRepository userRepository;

	private RankingService rankingService;

	@BeforeEach
	void setUp() {
		rankingService = new RankingService(
			rankingRepository,
			userRepository,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void 깜빡이_랭킹은_최고점수_내림차순이고_내_순위를_담는다() {
		givenBlink(List.of(
			score(1L, 100, T1),
			score(1L, 128, T2),
			score(2L, 116, T3),
			score(3L, 103, T4)
		));
		givenNicknames();

		GameRankingResponse response =
			rankingService.getGameRanking(1L, "BLINK", 1, 20);

		assertThat(response.rankType()).isEqualTo(RankType.BEST_SCORE);
		assertThat(response.unit()).isEqualTo("count");
		assertThat(response.rankings())
			.extracting(RankingEntry::userId)
			.containsExactly(1L, 2L, 3L);
		assertThat(response.rankings().get(0).value()).isEqualTo(128);
		assertThat(response.rankings().get(0).rank()).isEqualTo(1);
		assertThat(response.myRank().rank()).isEqualTo(1);
		assertThat(response.myRank().value()).isEqualTo(128);
		assertThat(response.totalElements()).isEqualTo(3);
	}

	@Test
	void 점수가_같으면_먼저_달성한_사람이_위다() {
		givenBlink(List.of(
			score(1L, 128, T2),
			score(2L, 128, T1)
		));
		givenNicknames();

		GameRankingResponse response =
			rankingService.getGameRanking(1L, "BLINK", 1, 20);

		assertThat(response.rankings())
			.extracting(RankingEntry::userId)
			.containsExactly(2L, 1L);
	}

	@Test
	void 점수가_0_이하이면_랭킹에서_제외한다() {
		givenBlink(List.of(
			score(1L, 128, T1),
			score(2L, 0, T2)
		));
		givenNicknames();

		GameRankingResponse response =
			rankingService.getGameRanking(1L, "BLINK", 1, 20);

		assertThat(response.rankings())
			.extracting(RankingEntry::userId)
			.containsExactly(1L);
	}

	@Test
	void 탈퇴한_회원은_랭킹에서_제외한다() {
		givenBlink(List.of(
			score(1L, 128, T1),
			score(2L, 116, T2)
		));
		when(userRepository.findAllById(any())).thenReturn(List.of(
			user(1L, "u1"),
			withdrawnUser(2L, "withdrawn-2")
		));

		GameRankingResponse response =
			rankingService.getGameRanking(1L, "BLINK", 1, 20);

		assertThat(response.rankings())
			.extracting(RankingEntry::userId)
			.containsExactly(1L);
	}

	@Test
	void 에어하키_랭킹은_승리_횟수_기준이고_패배는_무시한다() {
		when(rankingRepository.findWeeklyParticipants(
			eq(GameName.HOCKEY), eq(PlayMode.RANDOM), any(), any()
		)).thenReturn(List.of(
			win(1L, Outcome.WIN, T1),
			win(1L, Outcome.WIN, T2),
			win(1L, Outcome.WIN, T3),
			win(2L, Outcome.WIN, T2),
			win(2L, Outcome.WIN, T4),
			win(3L, Outcome.LOSE, T1)
		));
		givenNicknames();

		GameRankingResponse response =
			rankingService.getGameRanking(1L, "HOCKEY", 1, 20);

		assertThat(response.rankType()).isEqualTo(RankType.WIN_COUNT);
		assertThat(response.unit()).isEqualTo("win");
		assertThat(response.rankings())
			.extracting(RankingEntry::userId)
			.containsExactly(1L, 2L);
		assertThat(response.rankings().get(0).value()).isEqualTo(3);
		assertThat(response.rankings().get(1).value()).isEqualTo(2);
	}

	@Test
	void 승수가_같으면_먼저_도달한_사람이_위다() {
		when(rankingRepository.findWeeklyParticipants(
			eq(GameName.HOCKEY), eq(PlayMode.RANDOM), any(), any()
		)).thenReturn(List.of(
			win(1L, Outcome.WIN, T1),
			win(1L, Outcome.WIN, T2),
			win(2L, Outcome.WIN, T1),
			win(2L, Outcome.WIN, T3)
		));
		givenNicknames();

		GameRankingResponse response =
			rankingService.getGameRanking(1L, "HOCKEY", 1, 20);

		assertThat(response.rankings())
			.extracting(RankingEntry::userId)
			.containsExactly(1L, 2L);
	}

	@Test
	void 이번_주_기록이_없으면_내_순위는_null이다() {
		givenBlink(List.of(score(1L, 100, T1)));
		givenNicknames();

		GameRankingResponse response =
			rankingService.getGameRanking(99L, "BLINK", 1, 20);

		assertThat(response.myRank()).isNull();
	}

	@Test
	void 페이지네이션은_순위를_유지한다() {
		givenBlink(List.of(
			score(1L, 50, T1),
			score(2L, 40, T2),
			score(3L, 30, T3),
			score(4L, 20, T4)
		));
		givenNicknames();

		GameRankingResponse response =
			rankingService.getGameRanking(1L, "BLINK", 2, 2);

		assertThat(response.rankings())
			.extracting(RankingEntry::rank)
			.containsExactly(3, 4);
		assertThat(response.rankings())
			.extracting(RankingEntry::userId)
			.containsExactly(3L, 4L);
		assertThat(response.totalPages()).isEqualTo(2);
	}

	@Test
	void 지원하지_않는_게임은_RANKING_001이다() {
		assertThatThrownBy(() ->
			rankingService.getGameRanking(1L, "CHESS", 1, 20))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(RankingErrorCode.INVALID_GAME));
	}

	@Test
	void 홈_요약은_다섯_게임을_반환한다() {
		when(rankingRepository.findWeeklyParticipants(any(), any(), any(), any()))
			.thenReturn(List.of());
		when(userRepository.findAllById(any())).thenReturn(List.of());

		RankingListResponse response = rankingService.getRankings(1L, 3);

		assertThat(response.period()).isEqualTo("weekly");
		assertThat(response.games()).hasSize(5);
		assertThat(response.games())
			.extracting(game -> game.gameName())
			.containsExactlyInAnyOrder(
				GameName.HOCKEY, GameName.EYEFIGHT, GameName.DRAWING,
				GameName.RHYTHM, GameName.BLINK
			);
	}

	private void givenBlink(List<Participant> participants) {
		when(rankingRepository.findWeeklyParticipants(
			eq(GameName.BLINK), eq(PlayMode.SOLO), any(), any()
		)).thenReturn(participants);
	}

	private void givenNicknames() {
		when(userRepository.findAllById(any())).thenReturn(List.of(
			user(1L, "u1"), user(2L, "u2"), user(3L, "u3"), user(4L, "u4")
		));
	}

	private static Participant score(Long userId, long score, Instant endedAt) {
		return participant(userId, 1, Outcome.COMPLETED, score, endedAt);
	}

	private static Participant win(Long userId, Outcome outcome, Instant endedAt) {
		return participant(userId, 1, outcome, null, endedAt);
	}

	private static Participant participant(
		Long userId,
		int slotNo,
		Outcome outcome,
		Long score,
		Instant endedAt
	) {
		GameResult gameResult = GameResult.of(
			UUID.randomUUID(),
			null,
			Map.of(),
			endedAt.minusSeconds(60),
			endedAt
		);
		Participant participant = Participant.of(
			userId, ParticipantType.USER, slotNo, outcome, 1, "user" + userId, score
		);
		gameResult.addParticipant(participant);
		return participant;
	}

	private static User user(Long id, String nickname) {
		User user = User.createSocial(nickname);
		setField(User.class, user, "id", id);
		return user;
	}

	private static User withdrawnUser(Long id, String nickname) {
		User user = user(id, nickname);
		setField(User.class, user, "deletedAt", Instant.parse("2026-08-01T00:00:00Z"));
		return user;
	}

	private static void setField(
		Class<?> declaringClass,
		Object target,
		String fieldName,
		Object value
	) {
		try {
			Field field = declaringClass.getDeclaredField(fieldName);
			field.setAccessible(true);
			field.set(target, value);
		} catch (NoSuchFieldException | IllegalAccessException exception) {
			throw new IllegalStateException("테스트 필드 설정 실패", exception);
		}
	}
}
