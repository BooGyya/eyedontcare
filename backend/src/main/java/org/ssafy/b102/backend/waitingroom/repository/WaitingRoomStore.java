package org.ssafy.b102.backend.waitingroom.repository;

import java.util.Optional;
import java.util.UUID;

public interface WaitingRoomStore {

	CreateInviteRoomResult createInviteRoomAtomically(CreateInviteRoomCommand command);

	Optional<UUID> findRoomIdByInviteCode(String roomCode);

	JoinInviteRoomResult joinInviteRoomAtomically(JoinInviteRoomCommand command);

	Optional<WaitingRoomMetadata> findRoomMetadata(UUID roomId);

	Optional<WaitingRoomSnapshot> findSnapshot(UUID roomId);

	LeaveWaitingRoomResult leaveAtomically(LeaveWaitingRoomCommand command);
}
