package org.ssafy.b102.backend.matchmaking.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code gameType}을 enum이 아닌 문자열로 받는다.
 *
 * <p>enum으로 받으면 알 수 없는 값이 JSON 역직렬화 단계에서 걸려 공통 오류로 응답된다.
 * 명세서가 요구하는 "지원하지 않는 게임입니다."를 내려주려면 서비스 계층에서 변환해야 한다.
 * 나중에 {@code games} 테이블 존재 여부까지 검증할 자리도 같은 곳이다.
 */
public record MatchJoinRequest(

	@NotBlank(message = "게임 종류는 필수입니다.")
	String gameType
) {
}
