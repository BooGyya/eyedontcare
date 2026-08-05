package org.ssafy.b102.backend.group.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 소모임 후기 글 작성 요청. 최대 길이는 프론트 POST_MAX_LENGTH(500)와 동일하게 맞춰
 * 프론트 제한을 우회한 요청도 서버에서 막는다.
 */
public record GroupPostCreateRequest(

	@NotBlank(message = "후기 내용은 필수입니다.")
	@Size(max = 500, message = "후기는 500자 이하여야 합니다.")
	String content

) {
}
