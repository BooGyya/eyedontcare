package org.ssafy.b102.backend.game.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 그림 AI 채점 요청. 눈으로 그린 이미지(data URL)와 제시어, 후보 목록을 담는다.
 */
public record RecognizeDrawingRequest(

	@NotBlank(message = "그림 이미지가 필요합니다.")
	String imageDataUrl,

	@NotBlank(message = "제시어가 필요합니다.")
	String prompt,

	@NotEmpty(message = "후보 목록이 필요합니다.")
	List<String> candidates

) {
}
