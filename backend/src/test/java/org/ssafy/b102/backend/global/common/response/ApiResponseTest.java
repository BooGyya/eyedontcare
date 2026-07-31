package org.ssafy.b102.backend.global.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.global.error.CommonErrorCode;

class ApiResponseTest {

    @Test
    void successCreatesResponseWithData() {
        TestData data = new TestData(1L, "test");

        ApiResponse<TestData> response = ApiResponse.success(data);

        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("요청에 성공했습니다.");
        assertThat(response.data()).isEqualTo(data);
    }

    @Test
    void errorCreatesResponseFromErrorCode() {
        ApiResponse<Void> response = ApiResponse.error(
            CommonErrorCode.RESOURCE_NOT_FOUND
        );

        assertThat(response.code()).isEqualTo("COMMON-404");
        assertThat(response.message()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
        assertThat(response.data()).isNull();
    }

    @Test
    void validationErrorIsIncludedInData() {
        List<ValidationError> fieldErrors = List.of(
            new ValidationError(
                "email",
                "이메일 형식이 올바르지 않습니다."
            )
        );
        ValidationErrorResponse data = new ValidationErrorResponse(fieldErrors);

        ApiResponse<ValidationErrorResponse> response =
            ApiResponse.error(
                CommonErrorCode.INVALID_INPUT,
                data
            );

        assertThat(response.data().fieldErrors()).containsExactlyElementsOf(fieldErrors);
    }

    private record TestData(Long id, String name) {
    }
}
