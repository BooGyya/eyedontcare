package org.ssafy.b102.backend.auth.exception;

import org.springframework.http.HttpStatus;
import org.ssafy.b102.backend.global.error.ErrorCode;

public enum AuthErrorCode implements ErrorCode {

    EMAIL_ALREADY_EXISTS(
        HttpStatus.CONFLICT,
        "AUTH-001",
        "이미 사용 중인 이메일입니다."
    ),

    NICKNAME_GENERATION_FAILED(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "AUTH-002",
        "닉네임 생성에 실패했습니다."
    ),

    INVALID_CREDENTIALS(
        HttpStatus.UNAUTHORIZED,
        "AUTH-003",
        "이메일 또는 비밀번호가 올바르지 않습니다."
    ),

    INVALID_REFRESH_TOKEN(
        HttpStatus.UNAUTHORIZED,
        "AUTH-004",
        "유효하지 않은 리프레시 토큰입니다."
    ),

    USER_NOT_FOUND(
        HttpStatus.NOT_FOUND,
        "AUTH-005",
        "사용자를 찾을 수 없습니다."
    ),

    KAKAO_AUTHENTICATION_FAILED(
        HttpStatus.UNAUTHORIZED,
        "AUTH-006",
        "카카오 인증에 실패했습니다."
    ),

    KAKAO_SERVER_ERROR(
        HttpStatus.BAD_GATEWAY,
        "AUTH-007",
        "카카오 인증 서버와 통신할 수 없습니다."
    ),

    SOCIAL_ACCOUNT_CONFLICT(
        HttpStatus.CONFLICT,
        "AUTH-008",
        "소셜 계정 처리 중 충돌이 발생했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;

    AuthErrorCode(
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
