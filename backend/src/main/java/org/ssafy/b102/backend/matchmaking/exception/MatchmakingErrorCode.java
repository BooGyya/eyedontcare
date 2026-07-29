package org.ssafy.b102.backend.matchmaking.exception;

import org.springframework.http.HttpStatus;
import org.ssafy.b102.backend.global.error.ErrorCode;

public enum MatchmakingErrorCode implements ErrorCode {

	/**
	 * API 명세서의 {@code ALREADY_IN_QUEUE}.
	 */
	ALREADY_IN_QUEUE(
		HttpStatus.CONFLICT,
		"MATCHMAKING-001",
		"이미 매칭 대기 중입니다."
	),

	/**
	 * API 명세서의 {@code INVALID_GAME_TYPE}.
	 */
	INVALID_GAME_TYPE(
		HttpStatus.BAD_REQUEST,
		"MATCHMAKING-002",
		"지원하지 않는 게임입니다."
	),

	/**
	 * API 명세서에는 없다. 기능 정의서의 "SEARCHING 상태에서만 취소할 수 있다"를 지키기 위해 추가했다.
	 */
	REQUEST_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"MATCHMAKING-003",
		"진행 중인 매칭 신청이 없습니다."
	),

	/**
	 * API 명세서에는 없다. 기능 정의서의 "ENTERING_ROOM 전환 후에는 취소할 수 없다"를 지키기 위해 추가했다.
	 */
	CANCEL_NOT_ALLOWED(
		HttpStatus.CONFLICT,
		"MATCHMAKING-004",
		"매칭이 성사되어 취소할 수 없습니다."
	),

	/**
	 * 임시 인증 헤더 검증용. 인증 도메인이 완성되면 JWT 검증으로 대체된다.
	 */
	INVALID_PARTICIPANT_KEY(
		HttpStatus.BAD_REQUEST,
		"MATCHMAKING-005",
		"참가자 키 형식이 올바르지 않습니다."
	);

	private final HttpStatus status;
	private final String code;
	private final String message;

	MatchmakingErrorCode(HttpStatus status, String code, String message) {
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
