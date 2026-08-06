package org.ssafy.b102.backend.group.dto.response;

import java.util.List;

/**
 * 길드 목록·검색 응답(페이지네이션).
 */
public record GroupListResponse(
	List<GroupResponse> groups,
	int page,
	int size,
	long totalElements,
	int totalPages
) {
}
