package org.ssafy.b102.backend.gameresult.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.gameresult.GameResultSuccessCode;
import org.ssafy.b102.backend.gameresult.dto.response.GameResultDetailResponse;
import org.ssafy.b102.backend.gameresult.dto.response.MyGameResultPageResponse;
import org.ssafy.b102.backend.gameresult.service.GameResultQueryService;
import org.ssafy.b102.backend.global.common.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/game-results")
public class GameResultQueryController {

	/**
	 * 임시 인증 헤더. 인증 도메인이 완성되면 JWT에서 추출한 참가자 키로 대체한다.
	 */
	private static final String PARTICIPANT_KEY_HEADER = "X-Participant-Key";

	private static final String DEFAULT_PAGE = "1";
	private static final String DEFAULT_SIZE = "10";

	private final GameResultQueryService gameResultQueryService;

	public GameResultQueryController(GameResultQueryService gameResultQueryService) {
		this.gameResultQueryService = gameResultQueryService;
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<MyGameResultPageResponse>> getMyResults(
		@RequestHeader(PARTICIPANT_KEY_HEADER) String participantKey,
		@RequestParam(defaultValue = DEFAULT_PAGE)
		@Positive(message = "페이지 번호는 1 이상이어야 합니다.")
		int page,
		@RequestParam(defaultValue = DEFAULT_SIZE)
		@Positive(message = "페이지 크기는 1 이상이어야 합니다.")
		@Max(value = 50, message = "페이지 크기는 50을 넘을 수 없습니다.")
		int size
	) {
		MyGameResultPageResponse response = gameResultQueryService.getMyResults(participantKey, page, size);

		return ResponseEntity.ok(ApiResponse.success(GameResultSuccessCode.RESULT_LIST_FOUND, response));
	}

	@GetMapping("/{resultId}")
	public ResponseEntity<ApiResponse<GameResultDetailResponse>> getResult(
		@RequestHeader(PARTICIPANT_KEY_HEADER) String participantKey,
		@PathVariable Long resultId
	) {
		GameResultDetailResponse response = gameResultQueryService.getResult(participantKey, resultId);

		return ResponseEntity.ok(ApiResponse.success(GameResultSuccessCode.RESULT_FOUND, response));
	}
}
