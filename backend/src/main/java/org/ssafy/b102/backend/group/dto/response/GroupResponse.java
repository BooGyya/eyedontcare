package org.ssafy.b102.backend.group.dto.response;

import java.time.Instant;
import org.ssafy.b102.backend.group.entity.Group;
import org.ssafy.b102.backend.group.entity.GroupVisibility;

/**
 * 길드 카드 응답(생성·입장·목록 항목 공통). 프론트 CommunityGroup 모양에 맞춘다.
 *
 * <p>{@code joinCode}는 요청자가 가입한 경우에만 채우고, 비가입자는 null이다.
 */
public record GroupResponse(
	Long groupId,
	String name,
	String description,
	int members,
	int capacity,
	GroupVisibility visibility,
	String leader,
	boolean isOwner,
	boolean isJoined,
	String joinCode,
	Instant createdAt
) {

	public static GroupResponse of(
		Group group,
		int members,
		String leader,
		boolean isOwner,
		boolean isJoined
	) {
		return new GroupResponse(
			group.getId(),
			group.getName(),
			group.getDescription(),
			members,
			group.getCapacity(),
			group.getVisibility(),
			leader,
			isOwner,
			isJoined,
			isJoined ? group.getGroupCode() : null,
			group.getCreatedAt()
		);
	}
}
