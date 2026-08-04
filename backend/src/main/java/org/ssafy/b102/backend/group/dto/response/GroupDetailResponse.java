package org.ssafy.b102.backend.group.dto.response;

import java.time.Instant;
import java.util.List;
import org.ssafy.b102.backend.group.entity.Group;
import org.ssafy.b102.backend.group.entity.GroupVisibility;

/**
 * 소모임 상세 응답. 카드 필드에 멤버 명단({@code memberList})을 더한다.
 * {@code joinCode}는 가입자에게만 채운다.
 */
public record GroupDetailResponse(
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
	Instant createdAt,
	List<GroupMemberResponse> memberList
) {

	public static GroupDetailResponse of(
		Group group,
		int members,
		String leader,
		boolean isOwner,
		boolean isJoined,
		List<GroupMemberResponse> memberList
	) {
		return new GroupDetailResponse(
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
			group.getCreatedAt(),
			memberList
		);
	}
}
