package org.ssafy.b102.backend.global.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;
import org.ssafy.b102.backend.global.error.ErrorCode;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiResponse<T>(
	boolean success,
	String code,
	String message,
	T data,
	List<ValidationError> errors
) {

	private static final String SUCCESS_CODE = "SUCCESS";
	private static final String SUCCESS_MESSAGE = "요청에 성공했습니다.";

	public ApiResponse {
		errors = errors == null ? List.of() : List.copyOf(errors);
	}

	public static <T> ApiResponse<T> success(T data) {
		return success(SUCCESS_MESSAGE, data);
	}

	public static <T> ApiResponse<T> success(String message, T data) {
		return new ApiResponse<>(true, SUCCESS_CODE, message, data, List.of());
	}

	public static <T> ApiResponse<T> success(SuccessCode successCode, T data) {
		Objects.requireNonNull(successCode, "successCode는 null일 수 없습니다.");

		return new ApiResponse<>(true, successCode.code(), successCode.message(), data, List.of());
	}

	public static ApiResponse<Void> error(ErrorCode errorCode) {
		return error(errorCode, List.of());
	}

	public static ApiResponse<Void> error(ErrorCode errorCode, List<ValidationError> errors) {
		return new ApiResponse<>(false, errorCode.code(), errorCode.message(), null, errors);
	}
}
