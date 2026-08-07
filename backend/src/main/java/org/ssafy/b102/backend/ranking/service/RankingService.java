package org.ssafy.b102.backend.ranking.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.gameresult.entity.Outcome;
import org.ssafy.b102.backend.gameresult.entity.Participant;
import org.ssafy.b102.backend.ranking.entity.RankingTrend;
import org.ssafy.b102.backend.ranking.repository.RankingTrendRepository;
import org.ssafy.b102.backend.ranking.support.RankTrend;
import org.ssafy.b102.backend.ranking.dto.response.GameRankingResponse;
import org.ssafy.b102.backend.ranking.dto.response.GameRankingSummary;
import org.ssafy.b102.backend.ranking.dto.response.MyRank;
import org.ssafy.b102.backend.ranking.dto.response.RankingEntry;
import org.ssafy.b102.backend.ranking.dto.response.RankingListResponse;
import org.ssafy.b102.backend.ranking.repository.RankingRepository;
import org.ssafy.b102.backend.ranking.support.RankType;
import org.ssafy.b102.backend.ranking.support.RankingGame;
import org.ssafy.b102.backend.ranking.support.WeekRange;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.repository.UserRepository;

/**
 * 랭킹 조회 서비스(온-리드 집계).
 *
 * <p>이번 주 경기를 게임·모드로 걸러 유저별로 집계한다. BEST_SCORE는 {@code game_result[slot].score}
 * 최고값, WIN_COUNT는 승리 횟수. 동점은 먼저 달성한 순(선착순)으로 순위를 매긴다.
 * 데이터가 적은 MVP 단계라 조회 시 계산하며, 규모가 커지면 사전집계 테이블로 대체한다.
 */
@Service
public class RankingService {

	private static final String PERIOD = "weekly";

	private final RankingRepository rankingRepository;
	private final UserRepository userRepository;
	private final RankingTrendRepository rankingTrendRepository;
	private final Clock clock;

	@Autowired
	public RankingService(
		RankingRepository rankingRepository,
		UserRepository userRepository,
		RankingTrendRepository rankingTrendRepository
	) {
		this(rankingRepository, userRepository, rankingTrendRepository, Clock.systemUTC());
	}

	RankingService(
		RankingRepository rankingRepository,
		UserRepository userRepository,
		RankingTrendRepository rankingTrendRepository,
		Clock clock
	) {
		this.rankingRepository = rankingRepository;
		this.userRepository = userRepository;
		this.rankingTrendRepository = rankingTrendRepository;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public RankingListResponse getRankings(Long userId, int limit) {
		WeekRange week = WeekRange.current(clock.instant());

		List<GameRankingSummary> games = new ArrayList<>();
		for (RankingGame config : RankingGame.values()) {
			List<RankedUser> ranked = compute(config, week);
			List<RankedUser> top = ranked.size() > limit
				? ranked.subList(0, limit)
				: ranked;
			Map<Long, String> nicknames = nicknamesOf(top);
			Map<Long, String> trends =
				trendStringsFor(config.gameName(), top, week.weekStart());

			List<RankingEntry> topEntries = top.stream()
				.map(user -> new RankingEntry(
					user.rank(),
					user.userId(),
					nicknames.get(user.userId()),
					user.value(),
					null,
					trends.get(user.userId())
				))
				.toList();

			games.add(new GameRankingSummary(
				config.gameName(),
				config.rankType(),
				config.unit(),
				topEntries,
				findMyRank(ranked, userId)
			));
		}

		return new RankingListResponse(PERIOD, week.weekStart(), games);
	}

	@Transactional(readOnly = true)
	public GameRankingResponse getGameRanking(
		Long userId,
		String gameName,
		int page,
		int size
	) {
		RankingGame config = RankingGame.of(gameName);
		WeekRange week = WeekRange.current(clock.instant());
		List<RankedUser> ranked = compute(config, week);

		MyRank myRank = findMyRank(ranked, userId);

		int from = Math.max(0, (page - 1) * size);
		List<RankedUser> slice = from >= ranked.size()
			? List.of()
			: ranked.subList(from, Math.min(from + size, ranked.size()));
		Map<Long, String> nicknames = nicknamesOf(slice);
		Map<Long, String> trends =
			trendStringsFor(config.gameName(), slice, week.weekStart());

		List<RankingEntry> rankings = slice.stream()
			.map(user -> new RankingEntry(
				user.rank(),
				user.userId(),
				nicknames.get(user.userId()),
				user.value(),
				user.achievedAt(),
				trends.get(user.userId())
			))
			.toList();

		return new GameRankingResponse(
			config.gameName(),
			config.rankType(),
			config.unit(),
			PERIOD,
			week.weekStart(),
			rankings,
			myRank,
			page,
			size,
			ranked.size(),
			totalPages(ranked.size(), size)
		);
	}

	private List<RankedUser> compute(RankingGame config, WeekRange week) {
		List<Participant> participants = rankingRepository.findWeeklyParticipants(
			config.gameName(),
			config.rankedMode(),
			week.start(),
			week.end()
		);

		Map<Long, Aggregate> byUser = new HashMap<>();
		for (Participant participant : participants) {
			Long userId = participant.getUserId();
			Instant endedAt = participant.getGameResult().getEndedAt();

			if (config.rankType() == RankType.WIN_COUNT) {
				if (participant.getOutcome() != Outcome.WIN) {
					continue;
				}
				byUser.computeIfAbsent(userId, key -> new Aggregate())
					.addWin(endedAt);
			} else {
				Long score = participant.getScore();
				// 0 이하(점수 미계산·미달)는 랭킹에서 제외한다.
				if (score == null || score <= 0) {
					continue;
				}
				byUser.computeIfAbsent(userId, key -> new Aggregate())
					.addScore(score, endedAt);
			}
		}

		// 탈퇴한 회원(deletedAt != null)은 랭킹에서 제외한다(닉네임이 withdrawn-*로 남아 노출되던 문제).
		Map<Long, User> usersById = userRepository.findAllById(byUser.keySet()).stream()
			.collect(Collectors.toMap(User::getId, user -> user));
		byUser.keySet().removeIf(userId -> {
			User user = usersById.get(userId);
			return user != null && user.getDeletedAt() != null;
		});

		List<RankedUser> sorted = byUser.entrySet().stream()
			.map(entry -> new RankedUser(
				0,
				entry.getKey(),
				entry.getValue().value(),
				entry.getValue().achievedAt()
			))
			.sorted(Comparator.comparingLong(RankedUser::value).reversed()
				.thenComparing(RankedUser::achievedAt))
			.toList();

		List<RankedUser> ranked = new ArrayList<>(sorted.size());
		for (int index = 0; index < sorted.size(); index++) {
			RankedUser user = sorted.get(index);
			ranked.add(new RankedUser(
				index + 1,
				user.userId(),
				user.value(),
				user.achievedAt()
			));
		}
		return ranked;
	}

	/**
	 * 이번 주 특정 게임 랭킹에서 유저의 현재 순위. 랭킹에 없으면(점수 미달 등) null.
	 * 게임 종료 시점의 순위 변동 계산({@code RankingTrendService})에 쓴다.
	 */
	@Transactional(readOnly = true)
	public Integer rankOf(GameName gameName, Long userId) {
		if (userId == null) {
			return null;
		}
		RankingGame config = RankingGame.of(gameName);
		WeekRange week = WeekRange.current(clock.instant());
		return compute(config, week).stream()
			.filter(user -> user.userId().equals(userId))
			.map(RankedUser::rank)
			.findFirst()
			.orElse(null);
	}

	/** 표시할 유저들의 순위 변동(up/down/same)을 조회한다. 기록이 없으면 맵에서 빠진다. */
	private Map<Long, String> trendStringsFor(
		GameName gameName,
		List<RankedUser> users,
		LocalDate weekStart
	) {
		List<Long> ids = users.stream().map(RankedUser::userId).toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
		return rankingTrendRepository
			.findByGameNameAndWeekStartAndUserIdIn(gameName, weekStart, ids).stream()
			.collect(Collectors.toMap(
				RankingTrend::getUserId,
				trend -> trend.getTrend().name().toLowerCase()
			));
	}

	private MyRank findMyRank(List<RankedUser> ranked, Long userId) {
		return ranked.stream()
			.filter(user -> user.userId().equals(userId))
			.map(user -> new MyRank(user.rank(), user.value()))
			.findFirst()
			.orElse(null);
	}

	private Map<Long, String> nicknamesOf(List<RankedUser> users) {
		List<Long> ids = users.stream().map(RankedUser::userId).toList();
		return userRepository.findAllById(ids).stream()
			.collect(Collectors.toMap(User::getId, User::getNickname));
	}

	private int totalPages(long total, int size) {
		if (size <= 0) {
			return 0;
		}
		return (int) ((total + size - 1) / size);
	}

	private record RankedUser(
		int rank,
		Long userId,
		long value,
		Instant achievedAt
	) {
	}

	private static final class Aggregate {

		private long value;
		private Instant achievedAt;
		private boolean initialized;

		private void addScore(long score, Instant endedAt) {
			if (!initialized || score > value) {
				value = score;
				achievedAt = endedAt;
				initialized = true;
			} else if (score == value && endedAt.isBefore(achievedAt)) {
				achievedAt = endedAt;
			}
		}

		private void addWin(Instant endedAt) {
			value += 1;
			if (achievedAt == null || endedAt.isAfter(achievedAt)) {
				achievedAt = endedAt;
			}
			initialized = true;
		}

		private long value() {
			return value;
		}

		private Instant achievedAt() {
			return achievedAt;
		}
	}
}
