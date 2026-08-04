package org.ssafy.b102.backend.group.dto.response;

import java.time.Instant;
import org.ssafy.b102.backend.group.entity.GroupRole;

/**
 * 소모임 멤버 한 명. 닉네임은 users에서 조회해 채운다.
 */
public record GroupMemberResponse(
	Long userId,
	String nickname,
	GroupRole role,
	Instant joinedAt
) {
}
