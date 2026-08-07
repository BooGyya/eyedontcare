package org.ssafy.b102.backend.ranking.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.ranking.entity.RankingTrend;

public interface RankingTrendRepository extends JpaRepository<RankingTrend, Long> {

	Optional<RankingTrend> findByUserIdAndGameNameAndWeekStart(
		Long userId,
		GameName gameName,
		LocalDate weekStart
	);

	List<RankingTrend> findByGameNameAndWeekStartAndUserIdIn(
		GameName gameName,
		LocalDate weekStart,
		Collection<Long> userIds
	);
}
