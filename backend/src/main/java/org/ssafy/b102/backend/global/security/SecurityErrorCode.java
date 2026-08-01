package org.ssafy.b102.backend.global.security;

import org.springframework.http.HttpStatus;
import org.ssafy.b102.backend.global.error.ErrorCode;

public enum SecurityErrorCode implements ErrorCode {

    AUTHENTICATION_REQUIRED(
        HttpStatus.UNAUTHORIZED,
        "SECURITY-001",
        "인증이 필요합니다."
    ),

    INVALID_ACCESS_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "SECURITY-002",
        "유효하지 않은 액세스 토큰입니다."
    ),

    ACCESS_DENIED(
        HttpStatus.FORBIDDEN,
        "SECURITY-003",
        "접근 권한이 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    SecurityErrorCode(
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
