package org.ssafy.b102.backend.gameresult.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.ssafy.b102.backend.game.entity.Game;
import org.ssafy.b102.backend.global.common.entity.BaseTimeEntity;

@Entity
@Table(
	name = "games_results",
	uniqueConstraints = @UniqueConstraint(name = "uk_games_results_play_id", columnNames = "play_id")
)
public class GameResult extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "play_id", nullable = false, updatable = false)
	private UUID playId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "game_id", nullable = false)
	private Game game;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "game_result", nullable = false)
	private Map<String, Object> gameResult;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "ended_at", nullable = false)
	private Instant endedAt;

	@OneToMany(mappedBy = "gameResult", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Participant> participants = new ArrayList<>();

	protected GameResult() {
	}

	private GameResult(
		UUID playId,
		Game game,
		Map<String, Object> gameResult,
		Instant startedAt,
		Instant endedAt
	) {
		this.playId = playId;
		this.game = game;
		this.gameResult = gameResult;
		this.startedAt = startedAt;
		this.endedAt = endedAt;
	}

	public static GameResult of(
		UUID playId,
		Game game,
		Map<String, Object> gameResult,
		Instant startedAt,
		Instant endedAt
	) {
		return new GameResult(playId, game, gameResult, startedAt, endedAt);
	}

	public void addParticipant(Participant participant) {
		participants.add(participant);
		participant.assignTo(this);
	}

	public Long getId() {
		return id;
	}

	public UUID getPlayId() {
		return playId;
	}

	public Game getGame() {
		return game;
	}

	public Map<String, Object> getGameResult() {
		return gameResult;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public List<Participant> getParticipants() {
		return Collections.unmodifiableList(participants);
	}
}
