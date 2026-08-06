package org.ssafy.b102.backend.group.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * 길드 후기 글 한 건. 작성자 닉네임(author)과 방장 여부(isLeader)는 서비스에서 채우고,
 * 댓글 목록을 함께 담는다. 요청자 본인이 쓴 글은 {@code mine}으로 표시한다(수정·삭제
 * 버튼 노출 기준).
 */
public record GroupPostResponse(
	Long postId,
	String author,
	boolean isLeader,
	String content,
	Instant createdAt,
	boolean mine,
	List<GroupCommentResponse> comments
) {

	public GroupPostResponse {
		comments = comments == null ? List.of() : List.copyOf(comments);
	}
}
