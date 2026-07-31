package org.ssafy.b102.backend.waitingroom.repository;

import java.util.Optional;
import java.util.UUID;

public interface WaitingRoomStore {

	CreateInviteRoomResult createInviteRoomAtomically(CreateInviteRoomCommand command);

	boolean createRandomRoomAtomically(CreateRandomRoomCommand command);

	Optional<UUID> findRoomIdByInviteCode(String roomCode);

	JoinInviteRoomResult joinInviteRoomAtomically(JoinInviteRoomCommand command);

	Optional<WaitingRoomMetadata> findRoomMetadata(UUID roomId);

	Optional<WaitingRoomSnapshot> findSnapshot(UUID roomId);

	LeaveWaitingRoomResult leaveAtomically(LeaveWaitingRoomCommand command);

	RandomRoomLeaveResult leaveRandomRoomAtomically(
		LeaveRandomRoomCommand command
	);

	UpdateCalibrationResult updateCalibrationAtomically(
		UpdateCalibrationCommand command
	);

	UpdateReadyResult updateReadyAtomically(UpdateReadyCommand command);

	RandomReadyResult updateRandomReadyAtomically(
		UpdateReadyCommand command,
		UUID countdownId,
		java.time.Instant countdownEndsAt
	);

	StartInviteGameResult startInviteGameAtomically(
		StartInviteGameCommand command
	);

	CompleteCountdownResult completeCountdownAtomically(
		CompleteCountdownCommand command
	);

	RollbackCountdownResult rollbackCountdownAtomically(
		RollbackCountdownCommand command
	);
}
