package org.ssafy.b102.backend.matchmaking.websocket;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;
import org.ssafy.b102.backend.matchmaking.service.MatchNotifier;
import tools.jackson.databind.json.JsonMapper;

/**
 * 레지스트리에서 세션을 찾아 매칭 프레임을 전송하는 WebSocket 어댑터.
 *
 * <p>연결돼 있지 않거나 이미 닫힌 세션은 조용히 건너뛴다. 클라이언트가 신청 전에 WS를 연결하지
 * 못했거나 이미 끊긴 경우이며, REST 신청 응답이 fallback으로 실제 상태를 담고 있다.
 *
 * <p>전송 실패도 삼킨다. 알림은 매칭 성사의 곁가지이므로, 한쪽 전송이 실패해도 성사 자체나
 * 다른 참가자 알림을 되돌리지 않는다.
 */
@Component
public class WebSocketMatchNotifier implements MatchNotifier {

	private static final Logger log = LoggerFactory.getLogger(WebSocketMatchNotifier.class);

	private final MatchSessionRegistry registry;
	private final JsonMapper jsonMapper;

	public WebSocketMatchNotifier(MatchSessionRegistry registry, JsonMapper jsonMapper) {
		this.registry = registry;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void notifyMatched(String participantKey, UUID roomId, GameName gameType) {
		registry.find(participantKey)
			.filter(WebSocketSession::isOpen)
			.ifPresent(session -> send(session, participantKey, MatchNotification.matchSuccess(roomId, gameType)));
	}

	@Override
	public void notifyRequeued(String participantKey, GameName gameType) {
		registry.find(participantKey)
			.filter(WebSocketSession::isOpen)
			.ifPresent(session -> send(
				session,
				participantKey,
				MatchNotification.matchRequeued(gameType)
			));
	}

	@Override
	public void notifyError(String participantKey, MatchmakingErrorCode errorCode) {
		registry.find(participantKey)
			.filter(WebSocketSession::isOpen)
			.ifPresent(session -> send(
				session,
				participantKey,
				MatchNotification.matchError(errorCode)
			));
	}

	private void send(WebSocketSession session, String participantKey, MatchNotification notification) {
		try {
			session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(notification)));
		} catch (IOException | RuntimeException exception) {
			log.warn("매칭 알림 전송에 실패했습니다. participantKey={}", participantKey, exception);
		}
	}
}
