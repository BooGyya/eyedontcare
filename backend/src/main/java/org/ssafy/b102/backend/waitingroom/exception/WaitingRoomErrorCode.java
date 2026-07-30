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
	),
	WEBSOCKET_ALREADY_CONNECTED(
		HttpStatus.CONFLICT,
		"WAITING-010",
		"이미 연결된 대기방 세션이 있습니다."
	),
	INVALID_WEBSOCKET_MESSAGE(
		HttpStatus.BAD_REQUEST,
		"WAITING-011",
		"유효하지 않은 WebSocket 메시지입니다."
	),
	WEBSOCKET_AUTH_TIMEOUT(
		HttpStatus.UNAUTHORIZED,
		"WAITING-012",
		"WebSocket 인증 시간이 초과되었습니다."
	),
	CALIBRATION_REQUIRED(
		HttpStatus.BAD_REQUEST,
		"WAITING-013",
		"캘리브레이션을 먼저 진행하세요."
	),
	STATE_CHANGE_NOT_ALLOWED(
		HttpStatus.CONFLICT,
		"WAITING-014",
		"현재 상태에서는 요청한 상태로 변경할 수 없습니다."
	),
	GAME_START_FORBIDDEN(
		HttpStatus.FORBIDDEN,
		"WAITING-015",
		"방장만 게임을 시작할 수 있습니다."
	),
	PARTICIPANTS_NOT_READY(
		HttpStatus.CONFLICT,
		"WAITING-016",
		"다른 참가자가 아직 준비되지 않았어요."
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
