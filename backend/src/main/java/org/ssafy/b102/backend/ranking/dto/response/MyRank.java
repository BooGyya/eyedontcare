package org.ssafy.b102.backend.ranking.dto.response;

/**
 * 요청자 본인의 순위. 이번 주 기록이 없으면 응답에서 null이다.
 */
public record MyRank(
	int rank,
	long value
) {
}
