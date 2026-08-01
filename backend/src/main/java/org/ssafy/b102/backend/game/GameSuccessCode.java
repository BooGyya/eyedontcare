package org.ssafy.b102.backend.game;

import org.ssafy.b102.backend.global.common.response.SuccessCode;

public enum GameSuccessCode implements SuccessCode {

	GAME_LIST_FOUND("GAME_LIST_FOUND", "게임 목록을 조회했습니다."),
	GAME_FOUND("GAME_FOUND", "게임 상세를 조회했습니다.");

	private final String code;
	private final String message;

	GameSuccessCode(String code, String message) {
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
