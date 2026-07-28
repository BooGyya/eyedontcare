package org.ssafy.b102.backend.global.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void businessExceptionReturnsConfiguredHttpStatusAndErrorCode() throws Exception {
        mockMvc.perform(get("/test/business"))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("TEST-409"))
            .andExpect(jsonPath("$.message").value("이미 존재하는 테스트 데이터입니다."))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void invalidRequestBodyReturnsFieldValidationErrors() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-001"))
            .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("name"))
            .andExpect(jsonPath("$.data.fieldErrors[0].reason").value("이름은 필수입니다."));
    }

    @Test
    void malformedJsonReturnsBadRequestResponse() throws Exception {
        mockMvc.perform(post("/test/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-002"))
            .andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다."))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unsupportedHttpMethodReturnsMethodNotAllowedResponse() throws Exception {
        mockMvc.perform(post("/test/only-get"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("COMMON-405"))
            .andExpect(jsonPath("$.message").value("지원하지 않는 HTTP 메서드입니다."))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalMessage() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("COMMON-500"))
            .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(content().string(not(containsString("secret-internal-message"))));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/business")
        void business() {
            throw new BusinessException(TestErrorCode.DUPLICATE_DATA);
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/only-get")
        void onlyGet() {
        }

        @GetMapping("/unexpected")
        void unexpected() {
            throw new IllegalStateException("secret-internal-message");
        }
    }

    private record TestRequest(
        @NotBlank(message = "이름은 필수입니다.")
        String name
    ) {
    }

    private enum TestErrorCode implements ErrorCode {

        DUPLICATE_DATA(
            HttpStatus.CONFLICT,
            "TEST-409",
            "이미 존재하는 테스트 데이터입니다."
        );

        private final HttpStatus status;
        private final String code;
        private final String message;

        TestErrorCode(
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
}
