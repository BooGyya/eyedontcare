package org.ssafy.b102.backend.waitingroom;

import org.ssafy.b102.backend.global.common.response.SuccessCode;

public enum WaitingRoomSuccessCode implements SuccessCode {

	WAITING_ROOM_CREATE_SUCCESS(
		"WAITING_ROOM_CREATE_SUCCESS",
		"초대방 생성이 완료되었습니다."
	),
	WAITING_ROOM_JOIN_SUCCESS(
		"WAITING_ROOM_JOIN_SUCCESS",
		"초대방 입장이 완료되었습니다."
	),
	WAITING_ROOM_LEAVE_SUCCESS(
		"WAITING_ROOM_LEAVE_SUCCESS",
		"대기방 퇴장이 완료되었습니다."
	);

	private final String code;
	private final String message;

	WaitingRoomSuccessCode(String code, String message) {
		this.code = code;
		this.message = message;
	}

	@Override
	public String code() {
		return code;
	}

	@Override
	public String message() {
		return message;
	}
}
