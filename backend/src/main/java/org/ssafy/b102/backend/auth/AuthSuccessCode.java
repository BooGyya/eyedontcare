package org.ssafy.b102.backend.auth;

import org.ssafy.b102.backend.global.common.response.SuccessCode;

public enum AuthSuccessCode implements SuccessCode {

    SIGNUP_SUCCESS(
        "AUTH_SIGNUP_SUCCESS",
        "회원가입이 완료되었습니다."
    ),

    LOGIN_SUCCESS(
        "AUTH_LOGIN_SUCCESS",
        "로그인이 완료되었습니다."
    ),

    REISSUE_SUCCESS(
        "AUTH_REISSUE_SUCCESS",
        "토큰 재발급이 완료되었습니다."
    ),

    LOGOUT_SUCCESS(
        "AUTH_LOGOUT_SUCCESS",
        "로그아웃에 성공했습니다."
    );

    private final String code;
    private final String message;

    AuthSuccessCode(String code, String message) {
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
