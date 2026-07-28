package org.ssafy.b102.backend.ping.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.ssafy.b102.backend.ping.service.PingService;

class PingControllerTest {

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(new PingController(new PingService())).build();
	}

	@Test
	void pingReturnsWrappedPongResponse() throws Exception {
		mockMvc.perform(get("/api/ping"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.message").value("요청에 성공했습니다."))
			.andExpect(jsonPath("$.data.status").value("pong"));
	}
}
