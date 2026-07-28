package org.ssafy.b102.backend.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusinessExceptionTest {

	@Test
	void businessExceptionKeepsErrorCodeAndSafeMessage() {
		BusinessException exception = new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND);

		assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.RESOURCE_NOT_FOUND);
		assertThat(exception.getMessage()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
	}
}
