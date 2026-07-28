package org.ssafy.b102.backend.global.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;
import org.ssafy.b102.backend.global.error.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    String code,
    String message,
    T data
) {

    private static final String SUCCESS_CODE = "SUCCESS";
    private static final String SUCCESS_MESSAGE = "요청에 성공했습니다.";

    public static <T> ApiResponse<T> success(T data) {
        return success(SUCCESS_MESSAGE, data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(SUCCESS_CODE, message, data);
    }

    public static <T> ApiResponse<T> success(
        SuccessCode successCode,
        T data
    ) {
        Objects.requireNonNull(
            successCode,
            "successCode는 null일 수 없습니다."
        );

        return new ApiResponse<>(
            successCode.code(),
            successCode.message(),
            data
        );
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return error(errorCode, null);
    }

    public static <T> ApiResponse<T> error(
        ErrorCode errorCode,
        T data
    ) {
        Objects.requireNonNull(
            errorCode,
            "errorCode는 null일 수 없습니다."
        );

        return new ApiResponse<>(
            errorCode.code(),
            errorCode.message(),
            data
        );
    }
}
