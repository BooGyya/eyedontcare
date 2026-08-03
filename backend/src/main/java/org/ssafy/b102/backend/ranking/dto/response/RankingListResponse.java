package org.ssafy.b102.backend.ranking.dto.response;

import java.time.LocalDate;
import java.util.List;

/**
 * 홈 요약 랭킹 응답(게임별 TOP + 내 순위).
 */
public record RankingListResponse(
	String period,
	LocalDate weekStart,
	List<GameRankingSummary> games
) {
}
