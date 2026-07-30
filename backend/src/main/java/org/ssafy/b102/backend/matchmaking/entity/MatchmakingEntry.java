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
 *
 * <p>{@code matchAttemptId}는 예약({@code MATCHING})부터 방 배정까지 하나의 매칭 시도를
 * 식별한다. finalize·보상 시 이 값을 비교해 stale 콜백이 새 매칭을 건드리지 못하게 한다.
 */
public record MatchmakingEntry(
	String participantKey,
	GameName gameType,
	MatchStatus matchStatus,
	UUID waitingRoomId,
	Instant queuedAt,
	Instant statusChangedAt,
	UUID matchAttemptId
) {

	public MatchmakingEntry {
		queuedAt = queuedAt.truncatedTo(ChronoUnit.MILLIS);
		statusChangedAt = statusChangedAt.truncatedTo(ChronoUnit.MILLIS);
	}

	public static MatchmakingEntry searching(String participantKey, GameName gameType, Instant now) {
		return new MatchmakingEntry(participantKey, gameType, MatchStatus.SEARCHING, null, now, now, null);
	}

	/**
	 * 매칭 대상으로 예약한다. {@code queuedAt}은 유지하고 {@code matchAttemptId}를 부여한다.
	 */
	public MatchmakingEntry reserve(UUID matchAttemptId, Instant now) {
		return new MatchmakingEntry(
			participantKey,
			gameType,
			MatchStatus.MATCHING,
			null,
			queuedAt,
			now,
			matchAttemptId
		);
	}

	/**
	 * 매칭 성사. {@code queuedAt}과 {@code matchAttemptId}는 유지한다.
	 */
	public MatchmakingEntry enterRoom(UUID waitingRoomId, Instant now) {
		return new MatchmakingEntry(
			participantKey,
			gameType,
			MatchStatus.ENTERING_ROOM,
			waitingRoomId,
			queuedAt,
			now,
			matchAttemptId
		);
	}

	/**
	 * 대기방 WebSocket 입장 확인. {@code waitingRoomId}·{@code matchAttemptId}는 유지한다.
	 */
	public MatchmakingEntry enterWaitingRoom(Instant now) {
		return new MatchmakingEntry(
			participantKey,
			gameType,
			MatchStatus.IN_WAITING_ROOM,
			waitingRoomId,
			queuedAt,
			now,
			matchAttemptId
		);
	}

	public boolean isSearching() {
		return matchStatus == MatchStatus.SEARCHING;
	}

	/**
	 * 주어진 시도로 예약된 상태인지. finalize·보상·lifecycle에서 stale 콜백을 거른다.
	 */
	public boolean isReservedBy(UUID matchAttemptId) {
		return matchStatus == MatchStatus.MATCHING
			&& matchAttemptId != null
			&& matchAttemptId.equals(this.matchAttemptId);
	}

	public double queueScore() {
		return (double) queuedAt.toEpochMilli();
	}
}
