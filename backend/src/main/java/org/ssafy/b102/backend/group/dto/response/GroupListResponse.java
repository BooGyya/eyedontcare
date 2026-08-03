package org.ssafy.b102.backend.group.dto.response;

import java.util.List;

/**
 * 소모임 목록·검색 응답(페이지네이션).
 */
public record GroupListResponse(
	List<GroupSummaryResponse> groups,
	int page,
	int size,
	long totalElements,
	int totalPages
) {
}
