package org.ssafy.b102.backend.user;

import org.ssafy.b102.backend.global.common.response.SuccessCode;

public enum UserSuccessCode implements SuccessCode {

    USER_READ_SUCCESS(
        "USER_READ_SUCCESS",
        "내 정보 조회가 완료되었습니다."
    ),

    NICKNAME_CHECK_SUCCESS(
        "NICKNAME_CHECK_SUCCESS",
        "닉네임 중복 확인이 완료되었습니다."
    ),

    USER_UPDATE_SUCCESS(
        "USER_UPDATE_SUCCESS",
        "내 정보 수정이 완료되었습니다."
    ),

    PASSWORD_UPDATE_SUCCESS(
        "PASSWORD_UPDATE_SUCCESS",
        "비밀번호 변경이 완료되었습니다."
    );

    private final String code;
    private final String message;

    UserSuccessCode(String code, String message) {
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
