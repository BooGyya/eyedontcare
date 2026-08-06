package org.ssafy.b102.backend.group.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 길드 코드 입장 요청.
 */
public record GroupJoinRequest(

	@NotBlank(message = "길드 코드는 필수입니다.")
	@Size(min = 6, max = 6, message = "길드 코드는 6자여야 합니다.")
	String groupCode

) {
}
