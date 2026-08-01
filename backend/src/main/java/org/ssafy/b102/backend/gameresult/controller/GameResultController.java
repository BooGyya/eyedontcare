package org.ssafy.b102.backend.gameresult.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.gameresult.GameResultSuccessCode;
import org.ssafy.b102.backend.gameresult.dto.request.SubmitGameResultRequest;
import org.ssafy.b102.backend.gameresult.dto.response.SubmitGameResultResponse;
import org.ssafy.b102.backend.gameresult.service.GameResultService;
import org.ssafy.b102.backend.global.common.response.ApiResponse;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.guest.exception.GuestSessionErrorCode;
import org.ssafy.b102.backend.guest.support.GuestParticipantKey;

@RestController
@RequestMapping("/api/v1/game-results")
public class GameResultController {

	private static final String USER_KEY_PREFIX = "USER:";

	/**
	 * 게스트 제출자가 자기 세션을 밝히는 헤더. 회원은 이 헤더 대신 JWT로 식별된다.
	 * 제출자는 반드시 참가자 목록에 포함되어야 하므로, 게스트 세션의 실제 유효성은
	 * 서비스가 참가자를 검증하는 단계에서 함께 확인된다.
	 */
	private static final String GUEST_SESSION_HEADER = "X-Guest-Session-Id";

	private final GameResultService gameResultService;

	public GameResultController(GameResultService gameResultService) {
		this.gameResultService = gameResultService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<SubmitGameResultResponse>> submit(
		@AuthenticationPrincipal AuthenticatedUser member,
		@RequestHeader(value = GUEST_SESSION_HEADER, required = false) UUID guestSessionId,
		@Valid @RequestBody SubmitGameResultRequest request
	) {
		String requesterKey = resolveRequesterKey(member, guestSessionId);

		SubmitGameResultResponse response = gameResultService.submit(requesterKey, request);

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(GameResultSuccessCode.RESULT_SUBMITTED, response));
	}

	/**
	 * 제출자 참가자 키를 신뢰 가능한 출처에서만 만든다. 회원은 JWT principal, 게스트는 세션 헤더.
	 * 둘 다 없으면 제출자를 식별할 수 없으므로 거절한다.
	 */
	private static String resolveRequesterKey(AuthenticatedUser member, UUID guestSessionId) {
		if (member != null) {
			return USER_KEY_PREFIX + member.userId();
		}
		if (guestSessionId != null) {
			return new GuestParticipantKey(guestSessionId).value();
		}

		throw new BusinessException(GuestSessionErrorCode.INVALID_GUEST_SESSION);
	}
}
