package org.ssafy.b102.backend.gameresult.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.b102.backend.gameresult.entity.GameResult;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {

	boolean existsByPlayId(UUID playId);
}
