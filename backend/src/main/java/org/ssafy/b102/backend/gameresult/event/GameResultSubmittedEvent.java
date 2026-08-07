package org.ssafy.b102.backend.gameresult.event;

import java.util.Set;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;

/**
 * 경기 결과가 저장됐음을 알리는 도메인 이벤트.
 *
 * <p>랭킹 변동 갱신 등 결과 저장에 딸린 후처리를 이벤트로 분리해, 결과 저장(gameresult)이 랭킹
 * 도메인에 직접 의존하지 않게 한다. {@code memberUserIds}는 이 결과의 회원 참가자들이다.
 */
public record GameResultSubmittedEvent(
	GameName gameName,
	PlayMode playMode,
	Set<Long> memberUserIds
) {
}
