package org.ssafy.b102.backend.gameresult.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.ssafy.b102.backend.global.common.entity.BaseTimeEntity;

/**
 * 경기 참가자.
 *
 * <p>{@code userId}는 회원 참가자만 값을 가진다. 게스트와 AI는 {@code null}이며
 * 표시 이름은 {@code displayName}으로 보존한다.
 * {@code users} 테이블이 아직 없어 외래키 제약 없이 컬럼만 유지하고,
 * 사용자 도메인이 완성되면 제약을 추가한다.
 */
@Entity
@Table(
	name = "participants_",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_participants_result_slot",
		columnNames = {"result_id", "slot_no"}
	)
)
public class Participant extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "result_id", nullable = false)
	private GameResult gameResult;

	@Column(name = "user_id")
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "participant_type", nullable = false, length = 20)
	private ParticipantType participantType;

	@Column(name = "slot_no", nullable = false)
	private Integer slotNo;

	@Enumerated(EnumType.STRING)
	@Column(name = "outcome", nullable = false, length = 20)
	private Outcome outcome;

	@Column(name = "rank_no")
	private Integer rankNo;

	@Column(name = "display_name", length = 50)
	private String displayName;

	protected Participant() {
	}

	private Participant(
		Long userId,
		ParticipantType participantType,
		Integer slotNo,
		Outcome outcome,
		Integer rankNo,
		String displayName
	) {
		this.userId = userId;
		this.participantType = participantType;
		this.slotNo = slotNo;
		this.outcome = outcome;
		this.rankNo = rankNo;
		this.displayName = displayName;
	}

	public static Participant of(
		Long userId,
		ParticipantType participantType,
		Integer slotNo,
		Outcome outcome,
		Integer rankNo,
		String displayName
	) {
		return new Participant(userId, participantType, slotNo, outcome, rankNo, displayName);
	}

	void assignTo(GameResult gameResult) {
		this.gameResult = gameResult;
	}

	public Long getId() {
		return id;
	}

	public GameResult getGameResult() {
		return gameResult;
	}

	public Long getUserId() {
		return userId;
	}

	public ParticipantType getParticipantType() {
		return participantType;
	}

	public Integer getSlotNo() {
		return slotNo;
	}

	public Outcome getOutcome() {
		return outcome;
	}

	public Integer getRankNo() {
		return rankNo;
	}

	public String getDisplayName() {
		return displayName;
	}
}
