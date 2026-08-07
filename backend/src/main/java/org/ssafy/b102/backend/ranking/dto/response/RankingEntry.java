package org.ssafy.b102.backend.ranking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 랭킹 한 줄. {@code achievedAt}은 상세 조회에만 채우고, 홈 요약(top)에서는 null(생략)이다.
 * {@code trend}는 순위 변동(up/down/same)으로, 변동 기록이 없으면 null(생략)이다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RankingEntry(
	int rank,
	Long userId,
	String nickname,
	long value,
	Instant achievedAt,
	String trend
) {
}
