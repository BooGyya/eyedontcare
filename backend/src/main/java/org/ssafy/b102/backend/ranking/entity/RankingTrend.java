package org.ssafy.b102.backend.ranking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.common.entity.BaseTimeEntity;
import org.ssafy.b102.backend.ranking.support.RankTrend;

/**
 * 회원의 게임별·주차별 랭킹 변동 상태.
 *
 * <p>랭킹은 조회 시 실시간 집계라 "이전 순위"가 없다. 그래서 게임 종료(결과 제출) 시점에
 * 해당 게임 랭킹에서의 순위 변동을 계산해 이 테이블에 저장하고, 랭킹 조회 화면은 여기 담긴
 * 값을 상승/하락/유지 배지로 보여준다. (userId, gameName, weekStart)로 유일하며 주차가 바뀌면
 * 새 행이 생겨 자연스럽게 초기화된다.
 */
@Entity
@Table(
	name = "ranking_trends",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_ranking_trends_user_game_week",
		columnNames = {"user_id", "game_name", "week_start"}
	)
)
public class RankingTrend extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "game_name", nullable = false, length = 20)
	private GameName gameName;

	@Column(name = "week_start", nullable = false)
	private LocalDate weekStart;

	/** 직전 게임 종료 시점의 순위. 다음 게임 종료 때 이 값과 비교해 변동을 정한다. */
	@Column(name = "last_rank", nullable = false)
	private int lastRank;

	@Enumerated(EnumType.STRING)
	@Column(name = "trend", nullable = false, length = 10)
	private RankTrend trend;

	protected RankingTrend() {
	}

	private RankingTrend(
		Long userId,
		GameName gameName,
		LocalDate weekStart,
		int lastRank,
		RankTrend trend
	) {
		this.userId = userId;
		this.gameName = gameName;
		this.weekStart = weekStart;
		this.lastRank = lastRank;
		this.trend = trend;
	}

	public static RankingTrend of(
		Long userId,
		GameName gameName,
		LocalDate weekStart,
		int lastRank,
		RankTrend trend
	) {
		return new RankingTrend(userId, gameName, weekStart, lastRank, trend);
	}

	public void update(int lastRank, RankTrend trend) {
		this.lastRank = lastRank;
		this.trend = trend;
	}

	public Long getUserId() {
		return userId;
	}

	public GameName getGameName() {
		return gameName;
	}

	public LocalDate getWeekStart() {
		return weekStart;
	}

	public int getLastRank() {
		return lastRank;
	}

	public RankTrend getTrend() {
		return trend;
	}
}
