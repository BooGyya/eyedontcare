package org.ssafy.b102.backend.matchmaking.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.global.common.response.ApiResponse;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.matchmaking.MatchmakingSuccessCode;
import org.ssafy.b102.backend.matchmaking.dto.request.MatchJoinRequest;
import org.ssafy.b102.backend.matchmaking.dto.response.MatchStatusResponse;
import org.ssafy.b102.backend.matchmaking.service.MatchmakingService;
import org.ssafy.b102.backend.matchmaking.support.MatchParticipantResolver;
import org.ssafy.b102.backend.matchmaking.support.ResolvedParticipant;

@RestController
@RequestMapping("/api/v1/match")
public class MatchmakingController {

	/**
	 * 게스트가 기존 세션을 재사용할 때 보내는 헤더. 회원은 이 헤더 대신 JWT로 식별된다.
	 * 회원과 게스트가 모두 쓰는 엔드포인트라 인증 방식을 헤더/토큰으로 나눈다.
	 */
	private static final String GUEST_SESSION_HEADER = "X-Guest-Session-Id";

	private final MatchmakingService matchmakingService;
	private final MatchParticipantResolver participantResolver;

	public MatchmakingController(
		MatchmakingService matchmakingService,
		MatchParticipantResolver participantResolver
	) {
		this.matchmakingService = matchmakingService;
		this.participantResolver = participantResolver;
	}

	@PostMapping("/join")
	public ResponseEntity<ApiResponse<MatchStatusResponse>> join(
		@AuthenticationPrincipal AuthenticatedUser member,
		@RequestHeader(value = GUEST_SESSION_HEADER, required = false) UUID guestSessionId,
		@Valid @RequestBody MatchJoinRequest request
	) {
		ResolvedParticipant participant = participantResolver.resolveForJoin(member, guestSessionId);

		MatchStatusResponse response =
			matchmakingService.join(participant.participantKey(), request.gameType());
		if (participant.isGuest()) {
			response = response.withGuest(participant.guestSessionId(), participant.guestNickname());
		}

		return ResponseEntity.ok(ApiResponse.success(MatchmakingSuccessCode.MATCH_QUEUED, response));
	}

	@DeleteMapping("/cancel")
	public ResponseEntity<ApiResponse<MatchStatusResponse>> cancel(
		@AuthenticationPrincipal AuthenticatedUser member,
		@RequestHeader(value = GUEST_SESSION_HEADER, required = false) UUID guestSessionId
	) {
		String participantKey = participantResolver.resolveExistingKey(member, guestSessionId);

		MatchStatusResponse response = matchmakingService.cancel(participantKey);

		return ResponseEntity.ok(ApiResponse.success(MatchmakingSuccessCode.MATCH_CANCELLED, response));
	}
}
