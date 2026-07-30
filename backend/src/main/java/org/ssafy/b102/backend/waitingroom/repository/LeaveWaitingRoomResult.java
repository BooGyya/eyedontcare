package org.ssafy.b102.backend.waitingroom.repository;

public enum LeaveWaitingRoomResult {

	LEFT,
	ROOM_CLOSED,
	ALREADY_CLOSED,
	ROOM_NOT_FOUND,
	PARTICIPANT_NOT_FOUND,
	CORRUPTED
}
