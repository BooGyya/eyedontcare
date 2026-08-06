package org.ssafy.b102.backend.gameresult.dto.response;

import org.ssafy.b102.backend.gameresult.entity.Outcome;
import org.ssafy.b102.backend.gameresult.entity.Participant;
import org.ssafy.b102.backend.gameresult.entity.ParticipantType;

/**
 * 경기 상세 조회의 참가자 정보.
 *
 * <p>다른 참가자의 식별자를 노출하지 않기 위해 {@code participantKey}와 {@code userId}는 담지 않는다.
 * {@code score}는 랭킹 반영 점수(게스트/AI·점수 없는 경우 null)로, 전적 상세 표시에 쓴다.
 */
public record ParticipantResultResponse(
	Integer slotNo,
	ParticipantType participantType,
	String displayName,
	Outcome outcome,
	Integer rank,
	Long score
) {

	public static ParticipantResultResponse from(Participant participant) {
		return new ParticipantResultResponse(
			participant.getSlotNo(),
			participant.getParticipantType(),
			participant.getDisplayName(),
			participant.getOutcome(),
			participant.getRankNo(),
			participant.getScore()
		);
	}
}
