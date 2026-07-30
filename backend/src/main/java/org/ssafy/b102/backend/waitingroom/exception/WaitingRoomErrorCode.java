package org.ssafy.b102.backend.waitingroom.exception;

import org.springframework.http.HttpStatus;
import org.ssafy.b102.backend.global.error.ErrorCode;

public enum WaitingRoomErrorCode implements ErrorCode {

	INVALID_GAME_NAME(HttpStatus.BAD_REQUEST, "WAITING-001", "지원하지 않는 게임입니다."),
	INVITE_CODE_GENERATION_FAILED(
		HttpStatus.SERVICE_UNAVAILABLE,
		"WAITING-002",
		"초대 코드를 생성할 수 없습니다."
	),
	WAITING_ROOM_STORE_UNAVAILABLE(
		HttpStatus.SERVICE_UNAVAILABLE,
		"WAITING-003",
		"대기방을 생성할 수 없습니다."
	),
	INVALID_INVITE_CODE(
		HttpStatus.NOT_FOUND,
		"WAITING-004",
		"유효하지 않은 초대 코드입니다."
	),
	WAITING_ROOM_FULL(
		HttpStatus.CONFLICT,
		"WAITING-005",
		"대기방 정원이 가득 찼습니다."
	),
	PARTICIPANT_ALREADY_JOINED(
		HttpStatus.CONFLICT,
		"WAITING-006",
		"이미 참여 중인 대기방입니다."
	),
	WAITING_ROOM_NOT_JOINABLE(
		HttpStatus.CONFLICT,
		"WAITING-007",
		"현재 입장할 수 없는 대기방입니다."
	),
	WAITING_ROOM_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"WAITING-008",
		"대기방을 찾을 수 없습니다."
	),
	PARTICIPANT_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"WAITING-009",
		"대기방 참가자를 찾을 수 없습니다."
	);

	private final HttpStatus status;
	private final String code;
	private final String message;

	WaitingRoomErrorCode(HttpStatus status, String code, String message) {
		this.status = status;
		this.code = code;
		this.message = message;
	}

	@Override
	public HttpStatus status() {
		return status;
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
