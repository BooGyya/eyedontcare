package org.ssafy.b102.backend.gameresult.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.ssafy.b102.backend.gameresult.entity.Participant;

public interface ParticipantRepository extends JpaRepository<Participant, Long> {

	/**
	 * 특정 회원이 참가한 경기를 최신순으로 조회한다.
	 *
	 * <p>참가자 기준으로 조회하므로 경기당 한 행만 반환된다.
	 * 게임 정보를 함께 쓰기 때문에 fetch join으로 N+1을 막는다.
	 */
	@Query("""
		select p
		from Participant p
			join fetch p.gameResult r
			join fetch r.game
		where p.userId = :userId
		order by r.endedAt desc
		""")
	Page<Participant> findMyResults(Long userId, Pageable pageable);

	@Query("""
		select p
		from Participant p
		where p.gameResult.id = :resultId
			and p.userId = :userId
		""")
	Optional<Participant> findByResultIdAndUserId(Long resultId, Long userId);

	/**
	 * 특정 회원이 같은 게임에서 세운 이전 최고 점수를 조회한다.
	 *
	 * <p>신기록 판정을 위해 제출을 저장하기 <em>전에</em> 호출한다. 아직 저장 전이므로
	 * 이번 결과는 집계에 포함되지 않는다. 점수 기록이 없으면 빈 값이다.
	 * 게임(모드·난이도)마다 별도의 개인 최고를 갖도록 {@code gameId} 단위로 비교한다.
	 */
	@Query("""
		select max(p.score)
		from Participant p
		where p.userId = :userId
			and p.gameResult.game.id = :gameId
			and p.score is not null
		""")
	Optional<Long> findBestScore(Long userId, Long gameId);
}
