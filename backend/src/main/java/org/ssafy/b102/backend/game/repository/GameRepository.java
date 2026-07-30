package org.ssafy.b102.backend.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.b102.backend.game.entity.Game;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;

public interface GameRepository extends JpaRepository<Game, Long> {

	boolean existsByGameNameAndPlayMode(GameName gameName, PlayMode playMode);
}
