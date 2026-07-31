package org.ssafy.b102.backend.waitingroom.repository;

public record JoinInviteRoomResult(
	Status status,
	WaitingRoomSnapshot snapshot
) {

	public static JoinInviteRoomResult of(Status status) {
		return new JoinInviteRoomResult(status, null);
	}

	public static JoinInviteRoomResult joined(WaitingRoomSnapshot snapshot) {
		return new JoinInviteRoomResult(Status.JOINED, snapshot);
	}

	public enum Status {
		JOINED,
		INVALID_INVITE_CODE,
		NOT_JOINABLE,
		ALREADY_JOINED,
		FULL,
		CORRUPTED
	}
}
