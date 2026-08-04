package org.ssafy.b102.backend.game.exception;

import org.springframework.http.HttpStatus;
import org.ssafy.b102.backend.global.error.ErrorCode;

public enum GameErrorCode implements ErrorCode {

	GAME_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"GAME-001",
		"게임을 찾을 수 없습니다."
	),

	DRAWING_RECOGNITION_FAILED(
		HttpStatus.BAD_GATEWAY,
		"GAME-002",
		"그림 인식에 실패했습니다. 잠시 후 다시 시도해 주세요."
	),

	DRAWING_RECOGNITION_NOT_CONFIGURED(
		HttpStatus.SERVICE_UNAVAILABLE,
		"GAME-003",
		"그림 인식 서비스가 설정되지 않았습니다."
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
