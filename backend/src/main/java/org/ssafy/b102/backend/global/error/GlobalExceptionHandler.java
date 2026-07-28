package org.ssafy.b102.backend.global.error;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.ssafy.b102.backend.global.common.response.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	protected ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
		ErrorCode errorCode = exception.getErrorCode();
		log.warn("비즈니스 예외가 발생했습니다. code={}", errorCode.code());

		return ResponseEntity.status(errorCode.status()).body(ApiResponse.error(errorCode));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	protected ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(
		ConstraintViolationException exception
	) {
		log.warn("요청 값 검증에 실패했습니다. violations={}", exception.getConstraintViolations().size());

		return ResponseEntity.badRequest().body(ApiResponse.error(CommonErrorCode.INVALID_INPUT));
	}

	@ExceptionHandler(Exception.class)
	protected ResponseEntity<ApiResponse<Void>> handleUnexpectedException(Exception exception) {
		log.error("처리되지 않은 예외가 발생했습니다.", exception);

		return ResponseEntity.status(CommonErrorCode.INTERNAL_SERVER_ERROR.status())
			.body(ApiResponse.error(CommonErrorCode.INTERNAL_SERVER_ERROR));
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
		MethodArgumentNotValidException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		log.warn("요청 본문 검증에 실패했습니다. errors={}", exception.getBindingResult().getFieldErrorCount());

		return handleExceptionInternal(
			exception,
			ApiResponse.error(CommonErrorCode.INVALID_INPUT),
			headers,
			status,
			request
		);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(
		HandlerMethodValidationException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		log.warn("요청 파라미터 검증에 실패했습니다.");

		return handleExceptionInternal(
			exception,
			ApiResponse.error(CommonErrorCode.INVALID_INPUT),
			headers,
			status,
			request
		);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(
		HttpMessageNotReadableException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		return handleExceptionInternal(
			exception,
			ApiResponse.error(CommonErrorCode.MALFORMED_JSON),
			headers,
			status,
			request
		);
	}

	@Override
	protected ResponseEntity<Object> handleMissingServletRequestParameter(
		MissingServletRequestParameterException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		return handleExceptionInternal(
			exception,
			ApiResponse.error(CommonErrorCode.MISSING_PARAMETER),
			headers,
			status,
			request
		);
	}

	@Override
	protected ResponseEntity<Object> handleTypeMismatch(
		TypeMismatchException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		return handleExceptionInternal(
			exception,
			ApiResponse.error(CommonErrorCode.TYPE_MISMATCH),
			headers,
			status,
			request
		);
	}

	@Override
	protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
		HttpRequestMethodNotSupportedException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		return handleExceptionInternal(
			exception,
			ApiResponse.error(CommonErrorCode.METHOD_NOT_ALLOWED),
			headers,
			status,
			request
		);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
		HttpMediaTypeNotSupportedException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		return handleExceptionInternal(
			exception,
			ApiResponse.error(CommonErrorCode.MEDIA_TYPE_NOT_SUPPORTED),
			headers,
			status,
			request
		);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMediaTypeNotAcceptable(
		HttpMediaTypeNotAcceptableException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		return handleExceptionInternal(
			exception,
			ApiResponse.error(CommonErrorCode.NOT_ACCEPTABLE),
			headers,
			status,
			request
		);
	}

	@Override
	protected ResponseEntity<Object> handleNoHandlerFoundException(
		NoHandlerFoundException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		return handleExceptionInternal(
			exception,
			ApiResponse.error(CommonErrorCode.RESOURCE_NOT_FOUND),
			headers,
			status,
			request
		);
	}

	@Override
	protected ResponseEntity<Object> handleNoResourceFoundException(
		NoResourceFoundException exception,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		return handleExceptionInternal(
			exception,
			ApiResponse.error(CommonErrorCode.RESOURCE_NOT_FOUND),
			headers,
			status,
			request
		);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(
		Exception exception,
		Object body,
		HttpHeaders headers,
		HttpStatusCode status,
		WebRequest request
	) {
		Object responseBody = body instanceof ApiResponse<?>
			? body
			: ApiResponse.error(resolveErrorCode(status));

		return super.handleExceptionInternal(exception, responseBody, headers, status, request);
	}

	private ErrorCode resolveErrorCode(HttpStatusCode status) {
		return switch (status.value()) {
			case 404 -> CommonErrorCode.RESOURCE_NOT_FOUND;
			case 405 -> CommonErrorCode.METHOD_NOT_ALLOWED;
			case 406 -> CommonErrorCode.NOT_ACCEPTABLE;
			case 415 -> CommonErrorCode.MEDIA_TYPE_NOT_SUPPORTED;
			default -> status.is5xxServerError()
				? CommonErrorCode.INTERNAL_SERVER_ERROR
				: CommonErrorCode.INVALID_INPUT;
		};
	}
}
