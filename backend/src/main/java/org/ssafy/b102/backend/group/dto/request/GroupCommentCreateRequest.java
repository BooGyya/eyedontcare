package org.ssafy.b102.backend.group.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 소모임 댓글 작성 요청. 최대 길이는 프론트 COMMENT_MAX_LENGTH(200)와 동일하게 맞춰
 * 프론트 제한을 우회한 요청도 서버에서 막는다.
 */
public record GroupCommentCreateRequest(

	@NotBlank(message = "댓글 내용은 필수입니다.")
	@Size(max = 200, message = "댓글은 200자 이하여야 합니다.")
	String content

) {
}
