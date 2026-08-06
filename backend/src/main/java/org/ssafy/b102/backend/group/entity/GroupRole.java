package org.ssafy.b102.backend.group.entity;

/**
 * 길드 내 역할.
 *
 * <p>생성자는 {@code OWNER}, 코드로 입장한 회원은 {@code MEMBER}다.
 * 방장은 길드를 삭제·강퇴할 수 있고 나가기는 할 수 없다.
 */
public enum GroupRole {
	OWNER,
	MEMBER
}
