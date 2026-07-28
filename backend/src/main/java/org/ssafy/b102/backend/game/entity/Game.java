package org.ssafy.b102.backend.game.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.ssafy.b102.backend.global.common.entity.BaseTimeEntity;

@Entity
@Table(name = "games")
public class Game extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "game_name", nullable = false, length = 30)
	private GameName gameName;

	@Enumerated(EnumType.STRING)
	@Column(name = "play_mode", nullable = false, length = 20)
	private PlayMode playMode;

	@Column(name = "difficulty")
	private Integer difficulty;

	protected Game() {
	}

	private Game(GameName gameName, PlayMode playMode, Integer difficulty) {
		this.gameName = gameName;
		this.playMode = playMode;
		this.difficulty = difficulty;
	}

	public static Game of(GameName gameName, PlayMode playMode, Integer difficulty) {
		return new Game(gameName, playMode, difficulty);
	}

	public Long getId() {
		return id;
	}

	public GameName getGameName() {
		return gameName;
	}

	public PlayMode getPlayMode() {
		return playMode;
	}

	public Integer getDifficulty() {
		return difficulty;
	}
}
