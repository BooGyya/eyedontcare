package org.ssafy.b102.backend.ping.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.ping.exception.PingErrorCode;

class ErrorPingServiceTest {

	private final ErrorPingService errorPingService = new ErrorPingService();

	@Test
	void businessErrorThrowsBusinessExceptionWithPingErrorCode() {
		BusinessException exception = catchThrowableOfType(
			BusinessException.class,
			errorPingService::throwBusinessException
		);

		assertThat(exception.getErrorCode()).isEqualTo(PingErrorCode.BUSINESS_ERROR);
	}

	@Test
	void unexpectedErrorThrowsInternalException() {
		assertThatThrownBy(errorPingService::throwUnexpectedException)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("errorPing에서 의도적으로 발생시킨 내부 예외입니다.");
	}
}
