package org.ssafy.b102.backend.ranking.exception;

import org.springframework.http.HttpStatus;
import org.ssafy.b102.backend.global.error.ErrorCode;

public enum RankingErrorCode implements ErrorCode {

	INVALID_GAME(
		HttpStatus.BAD_REQUEST,
		"RANKING-001",
		"지원하지 않는 게임입니다."
	);

	private final HttpStatus status;
	private final String code;
	private final String message;

	RankingErrorCode(HttpStatus status, String code, String message) {
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
