package org.ssafy.b102.backend.gameresult;

import org.ssafy.b102.backend.global.common.response.SuccessCode;

public enum GameResultSuccessCode implements SuccessCode {

	RESULT_SUBMITTED("RESULT_SUBMITTED", "게임 결과가 저장되었습니다.");

	private final String code;
	private final String message;

	GameResultSuccessCode(String code, String message) {
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
