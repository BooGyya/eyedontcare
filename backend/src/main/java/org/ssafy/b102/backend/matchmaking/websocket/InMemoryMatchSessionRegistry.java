package org.ssafy.b102.backend.matchmaking.websocket;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * 단일 인스턴스용 in-memory 레지스트리.
 *
 * <p>참가자 키를 세션 속성에 심어두어, 연결 종료 시 세션만으로 키를 역추적한다.
 * 재연결로 같은 키가 새 세션에 매핑되면 옛 세션이 뒤늦게 닫혀도 새 세션을 지우지 않도록,
 * 값이 일치할 때만 제거한다.
 */
@Component
public class InMemoryMatchSessionRegistry implements MatchSessionRegistry {

	private static final String PARTICIPANT_KEY_ATTRIBUTE = "matchmaking.participantKey";

	private final Map<String, WebSocketSession> sessionsByKey = new ConcurrentHashMap<>();

	@Override
	public void register(String participantKey, WebSocketSession session) {
		session.getAttributes().put(PARTICIPANT_KEY_ATTRIBUTE, participantKey);
		sessionsByKey.put(participantKey, session);
	}

	@Override
	public Optional<WebSocketSession> find(String participantKey) {
		return Optional.ofNullable(sessionsByKey.get(participantKey));
	}

	@Override
	public Optional<String> unregister(WebSocketSession session) {
		Object participantKey = session.getAttributes().get(PARTICIPANT_KEY_ATTRIBUTE);
		if (!(participantKey instanceof String key)) {
			return Optional.empty();
		}

		sessionsByKey.remove(key, session);

		return Optional.of(key);
	}
}
