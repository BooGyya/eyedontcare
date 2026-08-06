package org.ssafy.b102.backend.group.dto.response;

import java.util.List;

/**
 * 내 길드 목록 응답.
 */
public record MyGroupListResponse(
	List<GroupResponse> groups
) {
}
