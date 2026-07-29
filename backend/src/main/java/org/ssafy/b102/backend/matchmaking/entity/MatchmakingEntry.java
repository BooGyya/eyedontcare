package org.ssafy.b102.backend.matchmaking.entity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.ssafy.b102.backend.game.entity.GameName;

/**
 * 매칭 참가자 상태. ERD의 {@code matchmaking_entries}에 대응하며 Redis Hash로 저장된다.
 *
 * <p>JPA 엔티티가 아니라 불변 값이다. 상태 전이는 새 인스턴스를 만들어 표현한다.
 *
 * <p>시각을 밀리초로 잘라 보관한다. 매칭 큐(Sorted Set)의 score가 {@code queuedAt}의
 * epoch milli이므로, 잘라두지 않으면 저장 후 다시 읽은 값이 원본과 달라진다.
 */
public record MatchmakingEntry(
	String participantKey,
	GameName gameType,
	MatchStatus matchStatus,
	UUID waitingRoomId,
	Instant queuedAt,
	Instant statusChangedAt
) {

	public MatchmakingEntry {
		queuedAt = queuedAt.truncatedTo(ChronoUnit.MILLIS);
		statusChangedAt = statusChangedAt.truncatedTo(ChronoUnit.MILLIS);
	}

	public static MatchmakingEntry searching(String participantKey, GameName gameType, Instant now) {
		return new MatchmakingEntry(participantKey, gameType, MatchStatus.SEARCHING, null, now, now);
	}

	/**
	 * 매칭 성사. {@code queuedAt}은 유지한다.
	 */
	public MatchmakingEntry enterRoom(UUID waitingRoomId, Instant now) {
		return new MatchmakingEntry(
			participantKey,
			gameType,
			MatchStatus.ENTERING_ROOM,
			waitingRoomId,
			queuedAt,
			now
		);
	}

	public boolean isSearching() {
		return matchStatus == MatchStatus.SEARCHING;
	}

	public double queueScore() {
		return (double) queuedAt.toEpochMilli();
	}
}
