package org.ssafy.b102.backend.group.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.ssafy.b102.backend.group.entity.GroupVisibility;

/**
 * 길드 생성 요청.
 *
 * <p>{@code capacity}는 선택이며, 없으면 서비스에서 기본값(50)을 적용한다.
 */
public record GroupCreateRequest(

	@NotBlank(message = "길드 이름은 필수입니다.")
	@Size(max = 50, message = "길드 이름은 50자 이하여야 합니다.")
	String name,

	@Size(max = 255, message = "소개는 255자 이하여야 합니다.")
	String description,

	@NotNull(message = "공개 범위는 필수입니다.")
	GroupVisibility visibility,

	@Min(value = 2, message = "정원은 2명 이상이어야 합니다.")
	@Max(value = 100, message = "정원은 100명 이하여야 합니다.")
	Integer capacity

) {
}
