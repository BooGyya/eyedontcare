package org.ssafy.b102.backend.ranking.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.gameresult.entity.Participant;

/**
 * 랭킹 집계용 조회.
 *
 * <p>이번 주(기간) 특정 게임·모드의 참가 기록을 경기 결과와 함께 가져온다. 점수는
 * {@code game_result} JSONB에서 슬롯별로 뽑으므로 결과를 fetch join으로 함께 로드한다.
 */
public interface RankingRepository extends Repository<Participant, Long> {

	@Query("""
		select p
		from Participant p
			join fetch p.gameResult r
			join fetch r.game g
		where g.gameName = :gameName
			and g.playMode = :playMode
			and p.userId is not null
			and r.endedAt >= :start
			and r.endedAt < :end
		""")
	List<Participant> findWeeklyParticipants(
		GameName gameName,
		PlayMode playMode,
		Instant start,
		Instant end
	);
}
