package org.ssafy.b102.backend.waitingroom.service;

import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.repository.LeaveWaitingRoomResult;
import org.ssafy.b102.backend.waitingroom.repository.RandomRoomLeaveResult;

public record WaitingRoomLeaveOutcome(
	RoomType roomType,
	LeaveWaitingRoomResult inviteResult,
	RandomRoomLeaveResult randomResult
) {

	public static WaitingRoomLeaveOutcome invite(LeaveWaitingRoomResult result) {
		return new WaitingRoomLeaveOutcome(RoomType.INVITE, result, null);
	}

	public static WaitingRoomLeaveOutcome random(RandomRoomLeaveResult result) {
		return new WaitingRoomLeaveOutcome(RoomType.RANDOM, null, result);
	}
}
