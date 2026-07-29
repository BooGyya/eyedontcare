package org.ssafy.b102.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.ssafy.b102.backend.matchmaking.websocket.MatchWebSocketHandler;

/**
 * WebSocket 엔드포인트 등록.
 *
 * <p>{@link CorsConfig}는 MVC CORS 설정이라 WebSocket 핸드셰이크에는 적용되지 않는다.
 * 그래서 핸들러 등록 시 {@code setAllowedOrigins}로 CORS를 다시 준다. 값은 MVC와 같은
 * {@code app.cors.allowed-origins}를 재사용한다.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private static final String MATCH_ENDPOINT = "/ws/match";

	private final MatchWebSocketHandler matchWebSocketHandler;
	private final String[] allowedOrigins;

	public WebSocketConfig(
		MatchWebSocketHandler matchWebSocketHandler,
		@Value("${app.cors.allowed-origins}") String[] allowedOrigins
	) {
		this.matchWebSocketHandler = matchWebSocketHandler;
		this.allowedOrigins = allowedOrigins.clone();
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(matchWebSocketHandler, MATCH_ENDPOINT)
			.setAllowedOrigins(allowedOrigins);
	}
}
