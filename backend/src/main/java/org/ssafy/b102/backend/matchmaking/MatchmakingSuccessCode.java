package org.ssafy.b102.backend.matchmaking;

import org.ssafy.b102.backend.global.common.response.SuccessCode;

public enum MatchmakingSuccessCode implements SuccessCode {

	MATCH_QUEUED("MATCH_QUEUED", "랜덤 매칭이 접수되었습니다."),
	MATCH_CANCELLED("MATCH_CANCELLED", "랜덤 매칭이 취소되었습니다.");

	private final String code;
	private final String message;

	MatchmakingSuccessCode(String code, String message) {
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
