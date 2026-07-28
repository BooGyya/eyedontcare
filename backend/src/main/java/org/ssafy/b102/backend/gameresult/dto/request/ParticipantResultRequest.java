package org.ssafy.b102.backend.gameresult.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.ssafy.b102.backend.gameresult.entity.Outcome;
import org.ssafy.b102.backend.gameresult.entity.ParticipantType;

public record ParticipantResultRequest(
	@NotBlank(message = "참가자 키는 필수입니다.")
	@Size(max = 50, message = "참가자 키는 50자를 넘을 수 없습니다.")
	String participantKey,

	@NotNull(message = "참가자 유형은 필수입니다.")
	ParticipantType participantType,

	@NotNull(message = "슬롯 번호는 필수입니다.")
	@Positive(message = "슬롯 번호는 1 이상이어야 합니다.")
	Integer slotNo,

	@NotBlank(message = "표시 이름은 필수입니다.")
	@Size(max = 50, message = "표시 이름은 50자를 넘을 수 없습니다.")
	String displayName,

	@NotNull(message = "경기 결과는 필수입니다.")
	Outcome outcome,

	@NotNull(message = "최종 순위는 필수입니다.")
	@Positive(message = "최종 순위는 1 이상이어야 합니다.")
	Integer rank
) {
}
