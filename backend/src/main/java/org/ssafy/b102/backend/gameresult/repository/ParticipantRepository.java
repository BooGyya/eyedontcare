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
}
