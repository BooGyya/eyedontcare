package org.ssafy.b102.backend.user.exception;

import org.springframework.http.HttpStatus;
import org.ssafy.b102.backend.global.error.ErrorCode;

public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "USER-001",
        "사용자를 찾을 수 없습니다."
    ),

    USER_ACCESS_DENIED(
        HttpStatus.FORBIDDEN,
        "USER-002",
        "해당 사용자 정보에 접근할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    UserErrorCode(
        HttpStatus status,
        String code,
        String message
    ) {
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
