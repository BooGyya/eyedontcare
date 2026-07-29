package org.ssafy.b102.backend.waitingroom.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ssafy.b102.backend.game.entity.GameName;

/**
 * 랜덤 매칭이 성사되면 WaitingRoom에 RANDOM 대기방 생성을 요청한다.
 *
 * <p>matchmaking이 대기방 저장소를 직접 건드리지 않도록 WaitingRoom 쪽에 인터페이스를 둔다.
 * 팀 규약상 다른 도메인의 Repository를 직접 호출할 수 없다.
 *
 * <p>방 생성 실패는 예외가 아니라 빈 {@link Optional}로 표현한다.
 * 기능 정의서가 실패를 정상 흐름의 분기(선점 해제 후 대기 유지)로 규정하기 때문이다.
 */
public interface RandomRoomCreator {

	/**
	 * @param gameType        대기방에서 진행할 게임
	 * @param participantKeys 매칭된 참가자 키. {@code USER:{id}} 또는 {@code GUEST:{sessionId}} 형식
	 * @return 생성된 대기방 ID. 생성에 실패하면 빈 값
	 */
	Optional<UUID> createRandomRoom(GameName gameType, List<String> participantKeys);
}
