package org.ssafy.b102.backend.group.dto.response;

import java.time.Instant;

/**
 * 소모임 댓글 한 건. 작성자 닉네임은 users에서 조회해 채우고, {@code mine}은 요청자가
 * 작성자 본인인지를 나타낸다(수정·삭제 버튼 노출 기준).
 */
public record GroupCommentResponse(
	Long commentId,
	String author,
	String content,
	Instant createdAt,
	boolean mine
) {
}
