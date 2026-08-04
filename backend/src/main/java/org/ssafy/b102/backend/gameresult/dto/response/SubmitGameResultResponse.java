package org.ssafy.b102.backend.gameresult.dto.response;

/**
 * 게임 결과 제출 응답.
 *
 * <p>{@code isNewRecord}는 제출자(회원)의 이번 점수가 같은 게임에서의 이전 개인 최고 점수를
 * 엄격히 넘었는지를 서버가 판정한 값이다. 이전 기록이 없으면 신기록으로 본다.
 * 게스트이거나 이번 결과에 점수가 없으면 {@code false}, {@code previousBestScore}는 {@code null}이다.
 * "신기록"처럼 사용자에게 확정적으로 보여주는 값은 새로고침·다른 기기·동시 저장에서도
 * 일관되도록 프런트가 아니라 서버가 판정한다.
 */
public record SubmitGameResultResponse(
	Long resultId,
	boolean isNewRecord,
	Long previousBestScore
) {

	public static SubmitGameResultResponse of(
		Long resultId,
		boolean isNewRecord,
		Long previousBestScore
	) {
		return new SubmitGameResultResponse(resultId, isNewRecord, previousBestScore);
	}
}
