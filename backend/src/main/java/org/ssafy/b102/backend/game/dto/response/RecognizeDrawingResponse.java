package org.ssafy.b102.backend.game.dto.response;

import java.util.List;

/**
 * 그림 AI 채점 결과. 프론트 draw.ts의 RecognizeDrawingResponse 계약에 맞춘다.
 */
public record RecognizeDrawingResponse(
	String label,
	double confidence,
	boolean isTarget,
	String reason,
	List<String> candidates,
	String model
) {
}
