package org.ssafy.b102.backend.group.dto.response;

import java.time.Instant;

/**
 * 소모임 댓글 한 건. 작성자 닉네임은 users에서 조회해 채운다.
 */
public record GroupCommentResponse(
	Long commentId,
	String author,
	String content,
	Instant createdAt
) {
}
