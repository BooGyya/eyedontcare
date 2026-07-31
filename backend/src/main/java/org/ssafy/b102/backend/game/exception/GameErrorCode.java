package org.ssafy.b102.backend.game.exception;

import org.springframework.http.HttpStatus;
import org.ssafy.b102.backend.global.error.ErrorCode;

public enum GameErrorCode implements ErrorCode {

	GAME_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"GAME-001",
		"게임을 찾을 수 없습니다."
	);

	private final HttpStatus status;
	private final String code;
	private final String message;

	GameErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	@Override
	public HttpStatus status() {
		return status;
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
