package org.ssafy.b102.backend.gameresult.exception;

import org.springframework.http.HttpStatus;
import org.ssafy.b102.backend.global.error.ErrorCode;

public enum GameResultErrorCode implements ErrorCode {

	DUPLICATE_RESULT(
		HttpStatus.CONFLICT,
		"GAMERESULT-001",
		"이미 제출된 결과입니다."
	),
	GAME_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"GAMERESULT-002",
		"게임을 찾을 수 없습니다."
	),
	INVALID_PARTICIPANTS(
		HttpStatus.BAD_REQUEST,
		"GAMERESULT-003",
		"참가자 정보가 올바르지 않습니다."
	),
	REQUESTER_NOT_PARTICIPANT(
		HttpStatus.BAD_REQUEST,
		"GAMERESULT-004",
		"경기 참가자만 결과를 제출할 수 있습니다."
	),
	INVALID_PLAY_PERIOD(
		HttpStatus.BAD_REQUEST,
		"GAMERESULT-005",
		"경기 시작 시각과 종료 시각이 올바르지 않습니다."
	);

	private final HttpStatus status;
	private final String code;
	private final String message;

	GameResultErrorCode(HttpStatus status, String code, String message) {
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
