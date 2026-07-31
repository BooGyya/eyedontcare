package org.ssafy.b102.backend.matchmaking.service;

import java.util.UUID;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;

/**
 * 매칭 성사를 참가자에게 알린다. 도메인이 전송 수단을 모르도록 두는 포트다.
 *
 * <p>현재 어댑터는 WebSocket이지만, 매칭 로직은 전송 경로를 알 필요가 없다.
 * 연결돼 있지 않은 참가자에 대한 알림은 조용히 버려지며(REST 응답이 fallback),
 * 알림 실패가 매칭 성사 자체를 되돌리지 않는다.
 */
public interface MatchNotifier {

	void notifyMatched(String participantKey, UUID roomId, GameName gameType);

	void notifyRequeued(String participantKey, GameName gameType);

	void notifyError(String participantKey, MatchmakingErrorCode errorCode);
}
