package org.ssafy.b102.backend.gameresult.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.gameresult.GameResultSuccessCode;
import org.ssafy.b102.backend.gameresult.dto.response.GameResultDetailResponse;
import org.ssafy.b102.backend.gameresult.dto.response.MyGameResultPageResponse;
import org.ssafy.b102.backend.gameresult.service.GameResultQueryService;
import org.ssafy.b102.backend.global.common.response.ApiResponse;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/game-results")
public class GameResultQueryController {

	private static final String DEFAULT_PAGE = "1";
	private static final String DEFAULT_SIZE = "10";

	private final GameResultQueryService gameResultQueryService;

	public GameResultQueryController(GameResultQueryService gameResultQueryService) {
		this.gameResultQueryService = gameResultQueryService;
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<MyGameResultPageResponse>> getMyResults(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@RequestParam(defaultValue = DEFAULT_PAGE)
		@Positive(message = "페이지 번호는 1 이상이어야 합니다.")
		int page,
		@RequestParam(defaultValue = DEFAULT_SIZE)
		@Positive(message = "페이지 크기는 1 이상이어야 합니다.")
		@Max(value = 50, message = "페이지 크기는 50을 넘을 수 없습니다.")
		int size
	) {
		MyGameResultPageResponse response = gameResultQueryService.getMyResults(
			authenticatedUser.userId(),
			page,
			size
		);

		return ResponseEntity.ok(ApiResponse.success(GameResultSuccessCode.RESULT_LIST_FOUND, response));
	}

	@GetMapping("/{resultId}")
	public ResponseEntity<ApiResponse<GameResultDetailResponse>> getResult(
		@AuthenticationPrincipal AuthenticatedUser authenticatedUser,
		@PathVariable Long resultId
	) {
		GameResultDetailResponse response = gameResultQueryService.getResult(
			authenticatedUser.userId(),
			resultId
		);

		return ResponseEntity.ok(ApiResponse.success(GameResultSuccessCode.RESULT_FOUND, response));
	}
}
