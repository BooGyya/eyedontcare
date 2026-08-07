package org.ssafy.b102.backend.group.exception;

import org.springframework.http.HttpStatus;
import org.ssafy.b102.backend.global.error.ErrorCode;

public enum GroupErrorCode implements ErrorCode {

	GROUP_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"GROUP-001",
		"길드를 찾을 수 없습니다."
	),

	INVALID_GROUP_CODE(
		HttpStatus.NOT_FOUND,
		"GROUP-002",
		"유효하지 않은 길드 코드입니다."
	),

	GROUP_FULL(
		HttpStatus.CONFLICT,
		"GROUP-003",
		"길드 정원이 가득 찼습니다."
	),

	ALREADY_JOINED(
		HttpStatus.CONFLICT,
		"GROUP-004",
		"이미 가입한 길드입니다."
	),

	NOT_A_MEMBER(
		HttpStatus.FORBIDDEN,
		"GROUP-005",
		"길드원이 아닙니다."
	),

	NOT_GROUP_OWNER(
		HttpStatus.FORBIDDEN,
		"GROUP-006",
		"길드 방장만 할 수 있습니다."
	),

	OWNER_CANNOT_LEAVE(
		HttpStatus.CONFLICT,
		"GROUP-007",
		"방장은 나갈 수 없습니다. 길드를 삭제하세요."
	),

	CANNOT_KICK_SELF(
		HttpStatus.CONFLICT,
		"GROUP-008",
		"자기 자신은 강퇴할 수 없습니다."
	),

	GROUP_CODE_GENERATION_FAILED(
		HttpStatus.SERVICE_UNAVAILABLE,
		"GROUP-009",
		"길드 코드를 생성할 수 없습니다."
	),

	MEMBER_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"GROUP-010",
		"강퇴할 길드원을 찾을 수 없습니다."
	),

	PRIVATE_GROUP_REQUIRES_CODE(
		HttpStatus.FORBIDDEN,
		"GROUP-011",
		"비공개 길드는 코드로만 입장할 수 있습니다."
	),

	POST_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"GROUP-012",
		"후기를 찾을 수 없습니다."
	),

	PRIVATE_GROUP_MEMBER_ONLY(
		HttpStatus.FORBIDDEN,
		"GROUP-013",
		"비공개 길드는 길드원만 접근할 수 있습니다."
	),

	COMMENT_NOT_FOUND(
		HttpStatus.NOT_FOUND,
		"GROUP-014",
		"댓글을 찾을 수 없습니다."
	),

	COMMENT_FORBIDDEN(
		HttpStatus.FORBIDDEN,
		"GROUP-015",
		"본인이 작성한 댓글만 수정·삭제할 수 있습니다."
	),

	POST_FORBIDDEN(
		HttpStatus.FORBIDDEN,
		"GROUP-016",
		"본인이 작성한 글만 수정·삭제할 수 있습니다."
	),

	DUPLICATE_GROUP_NAME(
		HttpStatus.CONFLICT,
		"GROUP-017",
		"이미 존재하는 길드 이름입니다. 다른 이름을 사용해 주세요."
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
