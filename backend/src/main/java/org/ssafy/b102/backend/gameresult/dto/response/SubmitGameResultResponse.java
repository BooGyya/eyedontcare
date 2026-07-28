package org.ssafy.b102.backend.gameresult.dto.response;

import org.ssafy.b102.backend.gameresult.entity.GameResult;

public record SubmitGameResultResponse(Long resultId) {

	public static SubmitGameResultResponse from(GameResult gameResult) {
		return new SubmitGameResultResponse(gameResult.getId());
	}
}
