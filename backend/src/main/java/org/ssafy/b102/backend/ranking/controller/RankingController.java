package org.ssafy.b102.backend.ranking.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.global.common.response.ApiResponse;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.ranking.RankingSuccessCode;
import org.ssafy.b102.backend.ranking.dto.response.GameRankingResponse;
import org.ssafy.b102.backend.ranking.dto.response.RankingListResponse;
import org.ssafy.b102.backend.ranking.service.RankingService;

@RestController
@RequestMapping("/api/v1/rankings")
public class RankingController {

	private final RankingService rankingService;

	public RankingController(RankingService rankingService) {
		this.rankingService = rankingService;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<RankingListResponse>> getRankings(
		@AuthenticationPrincipal AuthenticatedUser member,
		@RequestParam(defaultValue = "3") int limit
	) {
		RankingListResponse response =
			rankingService.getRankings(member.userId(), limit);

		return ResponseEntity.ok(ApiResponse.success(
			RankingSuccessCode.RANKING_LIST_FOUND,
			response
		));
	}

	@GetMapping("/{gameName}")
	public ResponseEntity<ApiResponse<GameRankingResponse>> getGameRanking(
		@AuthenticationPrincipal AuthenticatedUser member,
		@PathVariable String gameName,
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "20") int size
	) {
		GameRankingResponse response =
			rankingService.getGameRanking(member.userId(), gameName, page, size);

		return ResponseEntity.ok(ApiResponse.success(
			RankingSuccessCode.GAME_RANKING_FOUND,
			response
		));
	}
}
