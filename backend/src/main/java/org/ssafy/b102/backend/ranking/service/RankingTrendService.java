package org.ssafy.b102.backend.ranking.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.ranking.entity.RankingTrend;
import org.ssafy.b102.backend.ranking.repository.RankingTrendRepository;
import org.ssafy.b102.backend.ranking.support.RankTrend;
import org.ssafy.b102.backend.ranking.support.RankingGame;
import org.ssafy.b102.backend.ranking.support.WeekRange;

/**
 * 게임 종료(결과 제출) 시점의 랭킹 순위 변동을 계산·저장한다.
 *
 * <p>랭킹은 조회 시 실시간 집계라 "이전 순위"가 없어, 회원의 직전 게임 종료 시점 순위를
 * {@link RankingTrend}에 저장해 둔다. 새 게임이 끝나면 현재 순위를 다시 계산해 저장된 직전
 * 순위와 비교, 상승/하락/유지를 기록한다. 해당 (게임, 모드)가 랭킹 대상이 아니면 아무 것도
 * 하지 않는다. 결과 커밋 이후 이벤트로 호출되므로 새 결과가 이미 반영된 순위를 본다.
 */
@Service
public class RankingTrendService {

	private final RankingService rankingService;
	private final RankingTrendRepository rankingTrendRepository;
	private final Clock clock;

	@Autowired
	public RankingTrendService(
		RankingService rankingService,
		RankingTrendRepository rankingTrendRepository
	) {
		this(rankingService, rankingTrendRepository, Clock.systemUTC());
	}

	RankingTrendService(
		RankingService rankingService,
		RankingTrendRepository rankingTrendRepository,
		Clock clock
	) {
		this.rankingService = rankingService;
		this.rankingTrendRepository = rankingTrendRepository;
		this.clock = clock;
	}

	/**
	 * 방금 끝난 게임 결과를 반영해 회원들의 순위 변동을 갱신한다. 랭킹 대상 (게임, 모드)일 때만
	 * 동작하며, 각 유저의 현재 순위를 저장된 직전 순위와 비교한다. 랭킹에 오르지 못한 유저(점수
	 * 미달 등)는 건너뛴다. 이 주 첫 진입이면 변동은 유지(SAME)로 시작한다.
	 */
	@Transactional
	public void recordTrends(
		GameName gameName,
		PlayMode playMode,
		Collection<Long> userIds
	) {
		if (userIds.isEmpty() || !isRankedMode(gameName, playMode)) {
			return;
		}
		LocalDate weekStart = WeekRange.current(clock.instant()).weekStart();
		for (Long userId : userIds) {
			Integer currentRank = rankingService.rankOf(gameName, userId);
			if (currentRank == null) {
				continue;
			}
			upsert(userId, gameName, weekStart, currentRank);
		}
	}

	private void upsert(
		Long userId,
		GameName gameName,
		LocalDate weekStart,
		int currentRank
	) {
		Optional<RankingTrend> existing = rankingTrendRepository
			.findByUserIdAndGameNameAndWeekStart(userId, gameName, weekStart);

		if (existing.isPresent()) {
			RankingTrend trend = existing.get();
			trend.update(currentRank, trendOf(trend.getLastRank(), currentRank));
		} else {
			// 이번 주 첫 기록은 비교 대상이 없어 유지(SAME)로 시작한다.
			rankingTrendRepository.save(RankingTrend.of(
				userId, gameName, weekStart, currentRank, RankTrend.SAME));
		}
	}

	private RankTrend trendOf(int previousRank, int currentRank) {
		if (currentRank < previousRank) {
			return RankTrend.UP;
		}
		if (currentRank > previousRank) {
			return RankTrend.DOWN;
		}
		return RankTrend.SAME;
	}

	private boolean isRankedMode(GameName gameName, PlayMode playMode) {
		try {
			return RankingGame.of(gameName).rankedMode() == playMode;
		} catch (BusinessException exception) {
			return false;
		}
	}
}
