package org.ssafy.b102.backend.guest.exception;

import org.springframework.http.HttpStatus;
import org.ssafy.b102.backend.global.error.ErrorCode;

public enum GuestSessionErrorCode implements ErrorCode {

	INVALID_GUEST_SESSION(
		HttpStatus.UNAUTHORIZED,
		"GUEST-001",
		"유효하지 않은 게스트 세션입니다."
	),

	GUEST_SESSION_STORE_UNAVAILABLE(
		HttpStatus.SERVICE_UNAVAILABLE,
		"GUEST-002",
		"게스트 세션을 처리할 수 없습니다."
	);

	private final HttpStatus status;
	private final String code;
	private final String message;

	GuestSessionErrorCode(HttpStatus status, String code, String message) {
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
