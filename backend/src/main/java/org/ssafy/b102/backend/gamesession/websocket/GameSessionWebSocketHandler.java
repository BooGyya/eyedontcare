package org.ssafy.b102.backend.gamesession.websocket;

import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * {@code /ws/game-sessions/{roomId}} 핸들러. 경로에서 roomId를 뽑아 세션 서비스에 위임한다.
 * (대기방 핸들러와 동일한 얇은 위임 패턴)
 */
@Component
public class GameSessionWebSocketHandler extends TextWebSocketHandler {

	private final GameSessionWebSocketService service;

	public GameSessionWebSocketHandler(GameSessionWebSocketService service) {
		this.service = service;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		service.connectionEstablished(session, parseRoomId(session.getUri()));
	}

	@Override
	protected void handleTextMessage(
		WebSocketSession session,
		TextMessage message
	) {
		service.handleMessage(session, message.getPayload());
	}

	@Override
	public void afterConnectionClosed(
		WebSocketSession session,
		CloseStatus status
	) {
		service.connectionClosed(session);
	}

	@Override
	public void handleTransportError(
		WebSocketSession session,
		Throwable exception
	) {
		service.connectionClosed(session);
	}

	private UUID parseRoomId(URI uri) {
		if (uri == null) {
			return null;
		}
		String path = uri.getPath();
		int separator = path.lastIndexOf('/');
		if (separator < 0 || separator == path.length() - 1) {
			return null;
		}
		try {
			return UUID.fromString(path.substring(separator + 1));
		} catch (IllegalArgumentException exception) {
			return null;
		}
	}
}
