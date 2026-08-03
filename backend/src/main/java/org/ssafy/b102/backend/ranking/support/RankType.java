package org.ssafy.b102.backend.ranking.support;

/**
 * 랭킹 집계 방식.
 *
 * <p>{@code WIN_COUNT}은 승리 횟수 누적(에어하키), {@code BEST_SCORE}는 단판 최고 점수(그 외).
 */
public enum RankType {
	WIN_COUNT,
	BEST_SCORE
}
