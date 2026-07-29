package org.ssafy.b102.backend.matchmaking.websocket;

import java.util.Optional;
import org.springframework.web.socket.WebSocketSession;

/**
 * 참가자 키와 WebSocket 세션의 매핑을 보관한다.
 *
 * <p>매칭 성사 알림({@code MATCH_SUCCESS})은 구독이 아니라 특정 두 명에게 한 번 보내면 끝이므로,
 * 참가자 키로 세션을 직접 찾을 수 있어야 한다.
 *
 * <p>인터페이스로 분리한 이유는 다중 인스턴스 배포 때문이다. in-memory 구현은 두 참가자가 서로 다른
 * 인스턴스에 연결하면 깨지며, 그때 Redis Pub/Sub 구현으로 교체한다. Jenkins는 단일 인스턴스라
 * 당장은 in-memory로 충분하다.
 */
public interface MatchSessionRegistry {

	/**
	 * 참가자 키에 세션을 연결한다. 같은 키로 다시 등록하면 최신 세션으로 대체한다.
	 */
	void register(String participantKey, WebSocketSession session);

	Optional<WebSocketSession> find(String participantKey);

	/**
	 * 세션을 등록 해제한다. 연결 종료 시 참가자 키를 역추적하기 위해 세션을 받는다.
	 *
	 * @return 해제된 참가자 키. 등록된 적 없는 세션이면 빈 값
	 */
	Optional<String> unregister(WebSocketSession session);
}
