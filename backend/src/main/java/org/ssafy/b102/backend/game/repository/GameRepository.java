package org.ssafy.b102.backend.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.b102.backend.game.entity.Game;

public interface GameRepository extends JpaRepository<Game, Long> {
}
