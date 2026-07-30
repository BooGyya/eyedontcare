package org.ssafy.b102.backend.global.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.ssafy.b102.backend.global.security.SecurityAccessDeniedHandler;
import org.ssafy.b102.backend.global.security.SecurityAuthenticationEntryPoint;
import org.ssafy.b102.backend.global.security.SecurityErrorResponseWriter;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.ping.controller.PingController;
import org.ssafy.b102.backend.ping.service.PingService;
import org.ssafy.b102.backend.user.repository.UserRepository;

@WebMvcTest(PingController.class)
@Import({
	CorsConfig.class,
	SecurityConfig.class,
	SecurityAuthenticationEntryPoint.class,
	SecurityAccessDeniedHandler.class,
	SecurityErrorResponseWriter.class,
	PingService.class
})
@ImportAutoConfiguration(
	ServletWebSecurityAutoConfiguration.class
)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class CorsConfigTest {

	private static final String ALLOWED_ORIGIN = "http://localhost:5173";
	private static final String DISALLOWED_ORIGIN = "http://evil.example.com";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private JwtTokenProvider jwtTokenProvider;

	@MockitoBean
	private UserRepository userRepository;

	@Test
	void preflightFromAllowedOriginIsAccepted() throws Exception {
		mockMvc.perform(options("/api/ping")
				.header("Origin", ALLOWED_ORIGIN)
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
			.andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")));
	}

	/**
	 * 게스트는 회원 JWT 대신 {@code X-Guest-Session-Id} 헤더로 신원을 밝힌다.
	 * 이 헤더가 preflight에서 허용되지 않으면 브라우저 게스트 요청이 막힌다.
	 */
	@Test
	void preflightAllowsGuestSessionHeader() throws Exception {
		mockMvc.perform(options("/api/ping")
				.header("Origin", ALLOWED_ORIGIN)
				.header("Access-Control-Request-Method", "POST")
				.header("Access-Control-Request-Headers", "X-Guest-Session-Id"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Headers", containsString("X-Guest-Session-Id")));
	}

	@Test
	void preflightFromDisallowedOriginIsRejected() throws Exception {
		mockMvc.perform(options("/api/ping")
				.header("Origin", DISALLOWED_ORIGIN)
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isForbidden());
	}

	@Test
	void simpleRequestFromAllowedOriginReturnsAllowOriginHeader() throws Exception {
		mockMvc.perform(get("/api/ping").header("Origin", ALLOWED_ORIGIN))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
	}

	@Test
	void credentialsAreNotAllowed() throws Exception {
		mockMvc.perform(get("/api/ping").header("Origin", ALLOWED_ORIGIN))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
	}

	@Test
	void requestWithoutOriginHeaderIsUnaffected() throws Exception {
		mockMvc.perform(get("/api/ping"))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
	}
}
