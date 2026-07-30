package org.ssafy.b102.backend.waitingroom.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.waitingroom.entity.CalibrationStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomRole;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomSnapshot;

class WaitingRoomRoomStateTest {

	@Test
	void convertsSnapshotAndSortsParticipantsBySlot() {
		Instant now = Instant.parse("2026-07-30T04:00:00Z");
		UUID roomId =
			UUID.fromString("c93c76b2-7f78-4275-b8af-7cdd921bbb4f");
		WaitingRoomSnapshot snapshot = new WaitingRoomSnapshot(
			new WaitingRoom(
				roomId,
				RoomType.INVITE,
				GameName.EYEFIGHT,
				"0123",
				RoomStatus.WAITING,
				now
			),
			List.of(
				participant("USER:2", RoomRole.PLAYER, 2, now),
				participant("USER:1", RoomRole.HOST, 1, now)
			)
		);

		WaitingRoomRoomState state = WaitingRoomRoomState.from(snapshot);

		assertThat(state.roomId()).isEqualTo(roomId);
		assertThat(state.participants())
			.extracting(participant -> participant.slotNo())
			.containsExactly(1, 2);
	}

	private WaitingRoomParticipant participant(
		String key,
		RoomRole role,
		int slot,
		Instant joinedAt
	) {
		return new WaitingRoomParticipant(
			key,
			key,
			role,
			slot,
			false,
			CalibrationStatus.PENDING,
			joinedAt
		);
	}
}
