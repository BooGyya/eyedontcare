package org.ssafy.b102.backend.group.dto.response;

import java.util.List;

/**
 * 길드 후기 게시판 목록. 최신 글이 먼저 온다.
 */
public record GroupPostListResponse(
	List<GroupPostResponse> posts
) {

	public GroupPostListResponse {
		posts = posts == null ? List.of() : List.copyOf(posts);
	}
}
