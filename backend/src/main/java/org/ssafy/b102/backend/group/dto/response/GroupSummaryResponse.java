package org.ssafy.b102.backend.group.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.ssafy.b102.backend.group.entity.Group;
import org.ssafy.b102.backend.group.entity.GroupRole;
import org.ssafy.b102.backend.group.entity.GroupVisibility;

/**
 * 소모임 요약. 공개 목록에서는 {@code myRole}이 null(생략)이고,
 * 내 소모임 목록에서는 요청자의 역할이 채워진다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupSummaryResponse(
	Long groupId,
	String name,
	String description,
	int memberCount,
	int capacity,
	GroupVisibility visibility,
	GroupRole myRole
) {

	public static GroupSummaryResponse of(
		Group group,
		int memberCount,
		GroupRole myRole
	) {
		return new GroupSummaryResponse(
			group.getId(),
			group.getName(),
			group.getDescription(),
			memberCount,
			group.getCapacity(),
			group.getVisibility(),
			myRole
		);
	}
}
