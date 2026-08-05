package org.ssafy.b102.backend.group.exception;

import org.springframework.http.HttpStatus;
import org.ssafy.b102.backend.global.error.ErrorCode;

public enum GroupErrorCode implements ErrorCode {

	GROUP_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"GROUP-001",
		"소모임을 찾을 수 없습니다."
	),

	INVALID_GROUP_CODE(
		HttpStatus.NOT_FOUND,
		"GROUP-002",
		"유효하지 않은 소모임 코드입니다."
	),

	GROUP_FULL(
		HttpStatus.CONFLICT,
		"GROUP-003",
		"소모임 정원이 가득 찼습니다."
	),

	ALREADY_JOINED(
		HttpStatus.CONFLICT,
		"GROUP-004",
		"이미 가입한 소모임입니다."
	),

	NOT_A_MEMBER(
		HttpStatus.FORBIDDEN,
		"GROUP-005",
		"소모임 멤버가 아닙니다."
	),

	NOT_GROUP_OWNER(
		HttpStatus.FORBIDDEN,
		"GROUP-006",
		"소모임 방장만 할 수 있습니다."
	),

	OWNER_CANNOT_LEAVE(
		HttpStatus.CONFLICT,
		"GROUP-007",
		"방장은 나갈 수 없습니다. 소모임을 삭제하세요."
	),

	CANNOT_KICK_SELF(
		HttpStatus.CONFLICT,
		"GROUP-008",
		"자기 자신은 강퇴할 수 없습니다."
	),

	GROUP_CODE_GENERATION_FAILED(
		HttpStatus.SERVICE_UNAVAILABLE,
		"GROUP-009",
		"소모임 코드를 생성할 수 없습니다."
	),

	MEMBER_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"GROUP-010",
		"강퇴할 멤버를 찾을 수 없습니다."
	),

	PRIVATE_GROUP_REQUIRES_CODE(
		HttpStatus.FORBIDDEN,
		"GROUP-011",
		"비공개 소모임은 코드로만 입장할 수 있습니다."
	),

	POST_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"GROUP-012",
		"후기를 찾을 수 없습니다."
	),

	PRIVATE_GROUP_MEMBER_ONLY(
		HttpStatus.FORBIDDEN,
		"GROUP-013",
		"비공개 소모임은 멤버만 접근할 수 있습니다."
	);

	private final HttpStatus status;
	private final String code;
	private final String message;

	GroupErrorCode(HttpStatus status, String code, String message) {
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
