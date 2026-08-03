package org.ssafy.b102.backend.group.dto.response;

import org.ssafy.b102.backend.group.entity.Group;
import org.ssafy.b102.backend.group.entity.GroupRole;
import org.ssafy.b102.backend.group.entity.GroupVisibility;

/**
 * 소모임 생성·입장 응답. (멤버 목록은 상세 조회에서만 내려준다.)
 */
public record GroupResponse(
	Long groupId,
	String name,
	String description,
	String groupCode,
	GroupVisibility visibility,
	int capacity,
	int memberCount,
	Long ownerUserId,
	GroupRole myRole
) {

	public static GroupResponse of(
		Group group,
		int memberCount,
		GroupRole myRole
	) {
		return new GroupResponse(
			group.getId(),
			group.getName(),
			group.getDescription(),
			group.getGroupCode(),
			group.getVisibility(),
			group.getCapacity(),
			memberCount,
			group.getOwnerUserId(),
			myRole
		);
	}
}
