package org.ssafy.b102.backend.guest;

import org.ssafy.b102.backend.global.common.response.SuccessCode;

public enum GuestSessionSuccessCode implements SuccessCode {

	GUEST_SESSION_READY(
		"GUEST_SESSION_READY",
		"게스트 세션이 준비되었습니다."
	);

	private final String code;
	private final String message;

	GuestSessionSuccessCode(String code, String message) {
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
