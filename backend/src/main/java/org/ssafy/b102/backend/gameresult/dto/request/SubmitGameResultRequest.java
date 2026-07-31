package org.ssafy.b102.backend.gameresult.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 게임 결과 제출 요청.
 *
 * <p>{@code gameId}가 (게임 × 플레이 모드 × 난이도) 조합을 이미 식별하므로
 * 별도의 플레이 모드 필드는 받지 않는다.
 * {@code gameResult}는 게임별로 구조가 다르므로 서버에서 검증하지 않고 그대로 저장한다.
 */
public record SubmitGameResultRequest(
	@NotNull(message = "playId는 필수입니다.")
	UUID playId,

	@NotNull(message = "gameId는 필수입니다.")
	Long gameId,

	@NotNull(message = "경기 시작 시각은 필수입니다.")
	Instant startedAt,

	@NotNull(message = "경기 종료 시각은 필수입니다.")
	Instant endedAt,

	@NotEmpty(message = "참가자는 한 명 이상이어야 합니다.")
	@Valid
	List<ParticipantResultRequest> participants,

	@NotEmpty(message = "게임 상세 결과는 필수입니다.")
	Map<String, Object> gameResult
) {
}
