package org.ssafy.b102.backend.global.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.global.error.CommonErrorCode;

class ApiResponseTest {

	@Test
	void successCreatesSuccessfulResponseWithData() {
		TestData data = new TestData(1L, "test");

		ApiResponse<TestData> response = ApiResponse.success(data);

		assertThat(response.code()).isEqualTo("SUCCESS");
		assertThat(response.message()).isEqualTo("요청에 성공했습니다.");
		assertThat(response.data()).isEqualTo(data);
	}

	@Test
	void successCreatesSuccessfulResponseWithDomainSuccessCode() {
		TestData data = new TestData(1L, "test");

		ApiResponse<TestData> response = ApiResponse.success(TestSuccessCode.THING_FOUND, data);

		assertThat(response.code()).isEqualTo("THING_FOUND");
		assertThat(response.message()).isEqualTo("조회했습니다.");
		assertThat(response.data()).isEqualTo(data);
	}

	@Test
	void errorCreatesFailedResponseFromErrorCode() {
		ApiResponse<Void> response = ApiResponse.error(CommonErrorCode.RESOURCE_NOT_FOUND);

		assertThat(response.code()).isEqualTo("COMMON-404");
		assertThat(response.message()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
		assertThat(response.data()).isNull();
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
