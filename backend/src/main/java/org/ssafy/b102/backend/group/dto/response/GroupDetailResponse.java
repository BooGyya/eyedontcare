package org.ssafy.b102.backend.group.dto.response;

import java.util.List;
import org.ssafy.b102.backend.group.entity.Group;
import org.ssafy.b102.backend.group.entity.GroupRole;
import org.ssafy.b102.backend.group.entity.GroupVisibility;

/**
 * 소모임 상세 응답. {@code groupCode}는 멤버에게만 채워지고 비멤버는 null,
 * {@code myRole}도 비멤버는 null이다.
 */
public record GroupDetailResponse(
	Long groupId,
	String name,
	String description,
	String groupCode,
	GroupVisibility visibility,
	int capacity,
	int memberCount,
	Long ownerUserId,
	GroupRole myRole,
	List<GroupMemberResponse> members
) {

	public static GroupDetailResponse of(
		Group group,
		String groupCodeForMember,
		GroupRole myRole,
		List<GroupMemberResponse> members
	) {
		return new GroupDetailResponse(
			group.getId(),
			group.getName(),
			group.getDescription(),
			groupCodeForMember,
			group.getVisibility(),
			group.getCapacity(),
			members.size(),
			group.getOwnerUserId(),
			myRole,
			members
		);
	}
}
