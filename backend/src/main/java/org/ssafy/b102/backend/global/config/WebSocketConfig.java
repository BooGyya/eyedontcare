package org.ssafy.b102.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.ssafy.b102.backend.gamesession.websocket.GameSessionWebSocketHandler;
import org.ssafy.b102.backend.matchmaking.websocket.MatchWebSocketHandler;
import org.ssafy.b102.backend.waitingroom.websocket.WaitingRoomWebSocketHandler;

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
	private static final String WAITING_ROOM_ENDPOINT =
		"/ws/waiting-rooms/{roomId}";
	private static final String GAME_SESSION_ENDPOINT =
		"/ws/game-sessions/{roomId}";

	private final MatchWebSocketHandler matchWebSocketHandler;
	private final WaitingRoomWebSocketHandler waitingRoomWebSocketHandler;
	private final GameSessionWebSocketHandler gameSessionWebSocketHandler;
	private final String[] allowedOrigins;

	public WebSocketConfig(
		MatchWebSocketHandler matchWebSocketHandler,
		WaitingRoomWebSocketHandler waitingRoomWebSocketHandler,
		GameSessionWebSocketHandler gameSessionWebSocketHandler,
		@Value("${app.cors.allowed-origins}") String[] allowedOrigins
	) {
		this.matchWebSocketHandler = matchWebSocketHandler;
		this.waitingRoomWebSocketHandler = waitingRoomWebSocketHandler;
		this.gameSessionWebSocketHandler = gameSessionWebSocketHandler;
		this.allowedOrigins = allowedOrigins.clone();
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(matchWebSocketHandler, MATCH_ENDPOINT)
			.setAllowedOrigins(allowedOrigins);
		registry.addHandler(
				waitingRoomWebSocketHandler,
				WAITING_ROOM_ENDPOINT
			)
			.setAllowedOrigins(allowedOrigins);
		registry.addHandler(
				gameSessionWebSocketHandler,
				GAME_SESSION_ENDPOINT
			)
			.setAllowedOrigins(allowedOrigins);
	}

	@Bean(name = "waitingRoomWebSocketTaskScheduler")
	public static TaskScheduler waitingRoomWebSocketTaskScheduler() {
		ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
		scheduler.setPoolSize(1);
		scheduler.setThreadNamePrefix("waiting-room-ws-auth-");
		scheduler.setRemoveOnCancelPolicy(true);
		return scheduler;
	}
}
