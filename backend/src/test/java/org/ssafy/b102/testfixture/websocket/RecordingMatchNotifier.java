package org.ssafy.b102.testfixture.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;
import org.ssafy.b102.backend.matchmaking.service.MatchNotifier;

/**
 * 매칭 성사 시 어떤 참가자에게 알림이 나갔는지 기록하는 스텁.
 *
 * <p>전송 수단(WebSocket)을 끌어오지 않고 서비스가 알림 포트를 호출했는지만 검증한다.
 */
public class RecordingMatchNotifier implements MatchNotifier {

	public record Notified(String participantKey, UUID roomId, GameName gameType) {
	}

	private final List<Notified> notified = new ArrayList<>();

	@Override
	public void notifyMatched(String participantKey, UUID roomId, GameName gameType) {
		notified.add(new Notified(participantKey, roomId, gameType));
	}

	@Override
	public void notifyRequeued(String participantKey, GameName gameType) {
	}

	@Override
	public void notifyError(String participantKey, MatchmakingErrorCode errorCode) {
	}

	public List<Notified> notified() {
		return List.copyOf(notified);
	}

	public List<String> notifiedKeys() {
		return notified.stream().map(Notified::participantKey).toList();
	}

	/**
	 * 싱글톤 빈으로 재사용되므로 테스트마다 기록을 비운다.
	 */
	public void clear() {
		notified.clear();
	}
}
