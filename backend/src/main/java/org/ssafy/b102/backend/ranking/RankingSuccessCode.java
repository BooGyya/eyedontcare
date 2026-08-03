package org.ssafy.b102.backend.ranking;

import org.ssafy.b102.backend.global.common.response.SuccessCode;

public enum RankingSuccessCode implements SuccessCode {

	RANKING_LIST_FOUND(
		"RANKING_LIST_FOUND",
		"랭킹을 조회했습니다."
	),

	GAME_RANKING_FOUND(
		"GAME_RANKING_FOUND",
		"게임 랭킹을 조회했습니다."
	);

	private final String code;
	private final String message;

	RankingSuccessCode(String code, String message) {
		this.code = code;
		this.message = message;
	}

	@Override
	public String code() {
		return code;
	}

	@Override
	public String message() {
		return message;
	}
}
