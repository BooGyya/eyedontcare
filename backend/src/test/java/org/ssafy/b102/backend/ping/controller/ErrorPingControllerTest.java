package org.ssafy.b102.backend.ping.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ssafy.b102.backend.global.error.GlobalExceptionHandler;
import org.ssafy.b102.backend.ping.service.ErrorPingService;
import org.ssafy.b102.backend.ping.service.PingService;

class ErrorPingControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new ErrorPingController(
                    new ErrorPingService(),
                    new PingService()
                )
            )
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void businessErrorReturnsPingBusinessErrorResponse() throws Exception {
        mockMvc.perform(get("/api/ping/errors/business"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PING-001"))
            .andExpect(jsonPath("$.message")
                .value("의도적으로 발생시킨 Ping 비즈니스 예외입니다."))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void blankMessageReturnsValidationErrorResponse() throws Exception {
        mockMvc.perform(post("/api/ping/errors/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-001"))
            .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
            .andExpect(jsonPath("$.data.fieldErrors[0].field").value("message"))
            .andExpect(jsonPath("$.data.fieldErrors[0].reason")
                .value("메시지는 필수입니다."));
    }

    @Test
    void validMessageReturnsPongResponse() throws Exception {
        mockMvc.perform(post("/api/ping/errors/validation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"valid\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("SUCCESS"))
            .andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
            .andExpect(jsonPath("$.data.status").value("pong"));
    }

    @Test
    void unexpectedErrorReturnsSafeInternalServerErrorResponse() throws Exception {
        mockMvc.perform(get("/api/ping/errors/unexpected"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("COMMON-500"))
            .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
            .andExpect(jsonPath("$.data").doesNotExist())
            .andExpect(content().string(not(containsString(
                "errorPing에서 의도적으로 발생시킨 내부 예외입니다."
            ))));
    }
}
