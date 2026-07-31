package org.ssafy.b102.backend.waitingroom.websocket;

import java.net.URI;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class WaitingRoomWebSocketHandler extends TextWebSocketHandler {

	private final WaitingRoomWebSocketService webSocketService;

	public WaitingRoomWebSocketHandler(
		WaitingRoomWebSocketService webSocketService
	) {
		this.webSocketService = webSocketService;
	}

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		UUID roomId = parseRoomId(session.getUri());
		if (roomId == null) {
			webSocketService.invalidPath(session);
			return;
		}
		webSocketService.connectionEstablished(session, roomId);
	}

	@Override
	protected void handleTextMessage(
		WebSocketSession session,
		TextMessage message
	) {
		webSocketService.handleMessage(session, message.getPayload());
	}

	@Override
	public void afterConnectionClosed(
		WebSocketSession session,
		CloseStatus status
	) {
		webSocketService.connectionClosed(session);
	}

	@Override
	public void handleTransportError(
		WebSocketSession session,
		Throwable exception
	) {
		webSocketService.transportError(session);
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
