package org.ssafy.b102.backend.group;

import org.ssafy.b102.backend.global.common.response.SuccessCode;

public enum GroupSuccessCode implements SuccessCode {

	GROUP_CREATE_SUCCESS(
		"GROUP_CREATE_SUCCESS",
		"길드를 생성했습니다."
	),

	GROUP_LIST_FOUND(
		"GROUP_LIST_FOUND",
		"길드 목록을 조회했습니다."
	),

	GROUP_DETAIL_FOUND(
		"GROUP_DETAIL_FOUND",
		"길드를 조회했습니다."
	),

	GROUP_JOIN_SUCCESS(
		"GROUP_JOIN_SUCCESS",
		"길드에 가입했습니다."
	),

	MY_GROUP_LIST_FOUND(
		"MY_GROUP_LIST_FOUND",
		"내 길드 목록을 조회했습니다."
	),

	GROUP_LEAVE_SUCCESS(
		"GROUP_LEAVE_SUCCESS",
		"길드에서 나갔습니다."
	),

	GROUP_DELETE_SUCCESS(
		"GROUP_DELETE_SUCCESS",
		"길드를 삭제했습니다."
	),

	GROUP_MEMBER_KICK_SUCCESS(
		"GROUP_MEMBER_KICK_SUCCESS",
		"길드원을 강퇴했습니다."
	),

	GROUP_POST_LIST_FOUND(
		"GROUP_POST_LIST_FOUND",
		"후기 목록을 조회했습니다."
	),

	GROUP_POST_CREATE_SUCCESS(
		"GROUP_POST_CREATE_SUCCESS",
		"후기를 작성했습니다."
	),

	GROUP_COMMENT_CREATE_SUCCESS(
		"GROUP_COMMENT_CREATE_SUCCESS",
		"댓글을 작성했습니다."
	),

	GROUP_COMMENT_UPDATE_SUCCESS(
		"GROUP_COMMENT_UPDATE_SUCCESS",
		"댓글을 수정했습니다."
	),

	GROUP_COMMENT_DELETE_SUCCESS(
		"GROUP_COMMENT_DELETE_SUCCESS",
		"댓글을 삭제했습니다."
	);

	private final String code;
	private final String message;

	GroupSuccessCode(String code, String message) {
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
