package org.ssafy.b102.backend.ranking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.ranking.entity.RankingTrend;
import org.ssafy.b102.backend.ranking.repository.RankingTrendRepository;
import org.ssafy.b102.backend.ranking.support.RankTrend;

@ExtendWith(MockitoExtension.class)
class RankingTrendServiceTest {

	private static final Instant NOW = Instant.parse("2026-08-05T10:00:00Z");
	// 2026-08-05는 수요일 → 이번 주 월요일은 2026-08-03.
	private static final LocalDate WEEK_START = LocalDate.parse("2026-08-03");
	private static final Long USER_ID = 7L;

	@Mock
	private RankingService rankingService;

	@Mock
	private RankingTrendRepository rankingTrendRepository;

	private RankingTrendService trendService;

	@BeforeEach
	void setUp() {
		trendService = new RankingTrendService(
			rankingService,
			rankingTrendRepository,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void 이번_주_첫_기록은_SAME으로_저장한다() {
		when(rankingService.rankOf(GameName.BLINK, USER_ID)).thenReturn(3);
		when(rankingTrendRepository.findByUserIdAndGameNameAndWeekStart(
			eq(USER_ID), eq(GameName.BLINK), eq(WEEK_START)))
			.thenReturn(Optional.empty());

		trendService.recordTrends(GameName.BLINK, PlayMode.SOLO, Set.of(USER_ID));

		ArgumentCaptor<RankingTrend> captor =
			ArgumentCaptor.forClass(RankingTrend.class);
		verify(rankingTrendRepository).save(captor.capture());
		assertThat(captor.getValue().getTrend()).isEqualTo(RankTrend.SAME);
		assertThat(captor.getValue().getLastRank()).isEqualTo(3);
		assertThat(captor.getValue().getWeekStart()).isEqualTo(WEEK_START);
	}

	@Test
	void 직전보다_순위가_오르면_UP으로_갱신한다() {
		RankingTrend existing = RankingTrend.of(
			USER_ID, GameName.BLINK, WEEK_START, 3, RankTrend.SAME);
		when(rankingService.rankOf(GameName.BLINK, USER_ID)).thenReturn(1);
		when(rankingTrendRepository.findByUserIdAndGameNameAndWeekStart(
			eq(USER_ID), eq(GameName.BLINK), eq(WEEK_START)))
			.thenReturn(Optional.of(existing));

		trendService.recordTrends(GameName.BLINK, PlayMode.SOLO, Set.of(USER_ID));

		verify(rankingTrendRepository, never()).save(any());
		assertThat(existing.getTrend()).isEqualTo(RankTrend.UP);
		assertThat(existing.getLastRank()).isEqualTo(1);
	}

	@Test
	void 직전보다_순위가_내리면_DOWN으로_갱신한다() {
		RankingTrend existing = RankingTrend.of(
			USER_ID, GameName.BLINK, WEEK_START, 2, RankTrend.SAME);
		when(rankingService.rankOf(GameName.BLINK, USER_ID)).thenReturn(5);
		when(rankingTrendRepository.findByUserIdAndGameNameAndWeekStart(
			eq(USER_ID), eq(GameName.BLINK), eq(WEEK_START)))
			.thenReturn(Optional.of(existing));

		trendService.recordTrends(GameName.BLINK, PlayMode.SOLO, Set.of(USER_ID));

		assertThat(existing.getTrend()).isEqualTo(RankTrend.DOWN);
		assertThat(existing.getLastRank()).isEqualTo(5);
	}

	@Test
	void 랭킹에_오르지_못하면_기록하지_않는다() {
		when(rankingService.rankOf(GameName.BLINK, USER_ID)).thenReturn(null);

		trendService.recordTrends(GameName.BLINK, PlayMode.SOLO, Set.of(USER_ID));

		verify(rankingTrendRepository, never()).save(any());
	}

	@Test
	void 랭킹_대상_모드가_아니면_아무것도_하지_않는다() {
		// BLINK 랭킹은 SOLO만 대상 — INVITE 결과는 변동 기록 대상이 아니다.
		trendService.recordTrends(GameName.BLINK, PlayMode.INVITE, Set.of(USER_ID));

		verify(rankingTrendRepository, never()).save(any());
		verify(rankingTrendRepository, never())
			.findByUserIdAndGameNameAndWeekStart(any(), any(), any());
	}

	@Test
	void 대상_유저가_없으면_아무것도_하지_않는다() {
		trendService.recordTrends(GameName.BLINK, PlayMode.SOLO, List.of());

		verify(rankingTrendRepository, never()).save(any());
	}
}
