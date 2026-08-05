package org.ssafy.b102.backend.matchmaking.support;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.ssafy.b102.backend.matchmaking.repository.MatchmakingEntryRepository;
import org.ssafy.b102.backend.matchmaking.repository.MatchmakingEntryRepository.EntryDeleteResult;
import org.ssafy.b102.backend.waitingroom.service.RandomRoomLifecyclePort;

/**
 * WaitingRoom RANDOM 방의 생명주기를 Matchmaking entry에 반영하는 어댑터.
 *
 * <p>WaitingRoom은 MatchmakingService·Repository·Redis 키·상태 enum을 알지 못하고,
 * 이 어댑터를 통해서만 두 시점을 알린다.
 *
 * <ul>
 *   <li>입장 확인 → {@code ENTERING_ROOM} → {@code IN_WAITING_ROOM}</li>
 *   <li>IN_GAME 완료 → roomId 비교 후 entry 삭제(매칭 책임 종료)</li>
 * </ul>
 *
 * <p>bookkeeping 실패가 WaitingRoom 상태를 되돌리지 않아야 하므로, 대상이 아닌 호출은
 * 조용히 무시한다(roomId 불일치·stale 콜백). 게스트 세션 노출을 피하려고 참가자 키는 로그에 남기지 않는다.
 */
@Component
public class MatchmakingRandomRoomLifecycleAdapter implements RandomRoomLifecyclePort {

	private static final Logger log =
		LoggerFactory.getLogger(MatchmakingRandomRoomLifecycleAdapter.class);

	private final MatchmakingEntryRepository matchmakingEntryRepository;

	public MatchmakingRandomRoomLifecycleAdapter(MatchmakingEntryRepository matchmakingEntryRepository) {
		this.matchmakingEntryRepository = matchmakingEntryRepository;
	}

	@Override
	public void markParticipantEntered(UUID roomId, String participantKey) {
		boolean applied = matchmakingEntryRepository.markEntered(participantKey, roomId);
		if (!applied) {
			log.debug("입장 표시 대상 아님(stale 또는 roomId 불일치). roomId={}", roomId);
		}
	}

	@Override
	public void completeRandomRoom(UUID roomId, List<String> participantKeys) {
		participantKeys.forEach(key -> matchmakingEntryRepository.completeAndDelete(key, roomId));
	}

	@Override
	public void cleanupFailedParticipant(UUID roomId, String participantKey) {
		EntryDeleteResult result = matchmakingEntryRepository.deleteEnteringRoomIfMatches(
			participantKey,
			roomId
		);
		if (result == EntryDeleteResult.ROOM_MISMATCH) {
			log.debug("입장 실패 cleanup 대상 아님(stale 또는 roomId 불일치). roomId={}", roomId);
		}
	}
}
