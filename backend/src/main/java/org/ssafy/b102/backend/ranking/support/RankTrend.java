package org.ssafy.b102.backend.ranking.support;

/**
 * 랭킹 순위 변동 방향. 게임 종료(결과 제출) 시점에 이전 순위와 비교해 결정한다.
 *
 * <p>{@code UP} 순위 상승(숫자 작아짐), {@code DOWN} 하락(숫자 커짐), {@code SAME} 유지.
 */
public enum RankTrend {
	UP,
	DOWN,
	SAME
}
