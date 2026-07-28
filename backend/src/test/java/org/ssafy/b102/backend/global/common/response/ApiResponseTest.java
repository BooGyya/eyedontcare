package org.ssafy.b102.backend.global.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.global.error.CommonErrorCode;

class ApiResponseTest {

	@Test
	void successCreatesSuccessfulResponseWithData() {
		TestData data = new TestData(1L, "test");

		ApiResponse<TestData> response = ApiResponse.success(data);

		assertThat(response.success()).isTrue();
		assertThat(response.code()).isEqualTo("SUCCESS");
		assertThat(response.message()).isEqualTo("요청에 성공했습니다.");
		assertThat(response.data()).isEqualTo(data);
		assertThat(response.errors()).isEmpty();
	}

	@Test
	void successCreatesSuccessfulResponseWithDomainSuccessCode() {
		TestData data = new TestData(1L, "test");

		ApiResponse<TestData> response = ApiResponse.success(TestSuccessCode.THING_FOUND, data);

		assertThat(response.success()).isTrue();
		assertThat(response.code()).isEqualTo("THING_FOUND");
		assertThat(response.message()).isEqualTo("조회했습니다.");
		assertThat(response.data()).isEqualTo(data);
		assertThat(response.errors()).isEmpty();
	}

	@Test
	void errorCreatesFailedResponseFromErrorCode() {
		ApiResponse<Void> response = ApiResponse.error(CommonErrorCode.RESOURCE_NOT_FOUND);

		assertThat(response.success()).isFalse();
		assertThat(response.code()).isEqualTo("COMMON-404");
		assertThat(response.message()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
		assertThat(response.data()).isNull();
		assertThat(response.errors()).isEmpty();
	}

	@Test
	void errorCopiesValidationErrors() {
		List<ValidationError> errors = List.of(new ValidationError("email", "이메일 형식이 올바르지 않습니다."));

		ApiResponse<Void> response = ApiResponse.error(CommonErrorCode.INVALID_INPUT, errors);

		assertThat(response.errors()).containsExactlyElementsOf(errors);
	}

	private record TestData(Long id, String name) {
	}

	private enum TestSuccessCode implements SuccessCode {

		THING_FOUND("THING_FOUND", "조회했습니다.");

		private final String code;
		private final String message;

		TestSuccessCode(String code, String message) {
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
}
