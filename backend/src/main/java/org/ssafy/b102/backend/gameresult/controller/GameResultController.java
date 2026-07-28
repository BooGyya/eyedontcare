package org.ssafy.b102.backend.gameresult.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api/v1/game-results")
public class GameResultController {

	/**
	 * 임시 인증 헤더. 인증 도메인이 완성되면 JWT에서 추출한 참가자 키로 대체한다.
	 */
	private static final String PARTICIPANT_KEY_HEADER = "X-Participant-Key";

	private final GameResultService gameResultService;

	public GameResultController(GameResultService gameResultService) {
		this.gameResultService = gameResultService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<SubmitGameResultResponse>> submit(
		@RequestHeader(PARTICIPANT_KEY_HEADER) String participantKey,
		@Valid @RequestBody SubmitGameResultRequest request
	) {
		SubmitGameResultResponse response = gameResultService.submit(participantKey, request);

		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.success(GameResultSuccessCode.RESULT_SUBMITTED, response));
	}
}
