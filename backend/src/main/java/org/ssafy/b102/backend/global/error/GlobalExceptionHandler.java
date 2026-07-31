package org.ssafy.b102.backend.global.error;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
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
import org.ssafy.b102.backend.global.common.response.ValidationError;
import org.ssafy.b102.backend.global.common.response.ValidationErrorResponse;

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
    protected ResponseEntity<ApiResponse<ValidationErrorResponse>>
    handleConstraintViolationException(
        ConstraintViolationException exception
    ) {
        List<ValidationError> errors = exception.getConstraintViolations().stream()
            .map(violation -> new ValidationError(
                violation.getPropertyPath().toString(),
                violation.getMessage()
            ))
            .toList();

        return ResponseEntity.badRequest().body(
            ApiResponse.error(
                CommonErrorCode.INVALID_INPUT,
                new ValidationErrorResponse(errors)
            )
        );
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
        List<ValidationError> errors = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(error -> new ValidationError(
                error.getField(),
                error.getDefaultMessage()
            ))
            .toList();

        return handleExceptionInternal(
            exception,
            ApiResponse.error(
                CommonErrorCode.INVALID_INPUT,
                new ValidationErrorResponse(errors)
            ),
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
        Stream<ValidationError> parameterErrors =
            exception.getParameterValidationResults().stream()
                .flatMap(this::toValidationErrors);

        Stream<ValidationError> crossParameterErrors =
            exception.getCrossParameterValidationResults().stream()
                .map(error -> new ValidationError(
                    "request",
                    resolveMessage(error)
                ));

        List<ValidationError> errors = Stream.concat(
            parameterErrors,
            crossParameterErrors
        ).toList();

        return handleExceptionInternal(
            exception,
            ApiResponse.error(
                CommonErrorCode.INVALID_INPUT,
                new ValidationErrorResponse(errors)
            ),
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

	private Stream<ValidationError> toValidationErrors(ParameterValidationResult result) {
		String field = result.getMethodParameter().getParameterName();
		if (field == null) {
			field = "parameter[" + result.getMethodParameter().getParameterIndex() + "]";
		}
		String resolvedField = field;

		return result.getResolvableErrors().stream()
			.map(error -> new ValidationError(resolvedField, resolveMessage(error)));
	}

	private String resolveMessage(MessageSourceResolvable error) {
		String message = error.getDefaultMessage();
		return message == null ? CommonErrorCode.INVALID_INPUT.message() : message;
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
