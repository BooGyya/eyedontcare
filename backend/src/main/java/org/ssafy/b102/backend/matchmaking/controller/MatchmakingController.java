package org.ssafy.b102.backend.matchmaking.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.global.common.response.ApiResponse;
import org.ssafy.b102.backend.matchmaking.MatchmakingSuccessCode;
import org.ssafy.b102.backend.matchmaking.dto.request.MatchJoinRequest;
import org.ssafy.b102.backend.matchmaking.dto.response.MatchStatusResponse;
import org.ssafy.b102.backend.matchmaking.service.MatchmakingService;

@RestController
@RequestMapping("/api/v1/match")
public class MatchmakingController {

	/**
	 * 임시 인증 헤더. 인증 도메인이 완성되면 JWT에서 추출한 참가자 키로 대체한다.
	 */
	private static final String PARTICIPANT_KEY_HEADER = "X-Participant-Key";

	private final MatchmakingService matchmakingService;

	public MatchmakingController(MatchmakingService matchmakingService) {
		this.matchmakingService = matchmakingService;
	}

	@PostMapping("/join")
	public ResponseEntity<ApiResponse<MatchStatusResponse>> join(
		@RequestHeader(PARTICIPANT_KEY_HEADER) String participantKey,
		@Valid @RequestBody MatchJoinRequest request
	) {
		MatchStatusResponse response = matchmakingService.join(participantKey, request.gameType());

		return ResponseEntity.ok(ApiResponse.success(MatchmakingSuccessCode.MATCH_QUEUED, response));
	}

	@DeleteMapping("/cancel")
	public ResponseEntity<ApiResponse<MatchStatusResponse>> cancel(
		@RequestHeader(PARTICIPANT_KEY_HEADER) String participantKey
	) {
		MatchStatusResponse response = matchmakingService.cancel(participantKey);

		return ResponseEntity.ok(ApiResponse.success(MatchmakingSuccessCode.MATCH_CANCELLED, response));
	}
}
