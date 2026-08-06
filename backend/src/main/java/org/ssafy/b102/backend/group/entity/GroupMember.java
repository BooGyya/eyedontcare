package org.ssafy.b102.backend.group.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 길드원.
 *
 * <p>{@code group_id}는 {@code groups_}를, {@code user_id}는 {@code users}를 참조한다.
 * 한 길드에 같은 회원이 중복 가입하지 못하도록 (group_id, user_id) 유니크 제약을 둔다.
 */
@Entity
@Table(
	name = "group_members",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_group_members_group_user",
		columnNames = {"group_id", "user_id"}
	)
)
public class GroupMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "group_id", nullable = false)
	private Long groupId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private GroupRole role;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	protected GroupMember() {
	}

	private GroupMember(
		Long groupId,
		Long userId,
		GroupRole role,
		Instant joinedAt
	) {
		this.groupId = groupId;
		this.userId = userId;
		this.role = role;
		this.joinedAt = joinedAt;
	}

	public static GroupMember of(
		Long groupId,
		Long userId,
		GroupRole role,
		Instant joinedAt
	) {
		return new GroupMember(groupId, userId, role, joinedAt);
	}

	public Long getId() {
		return id;
	}

	public Long getGroupId() {
		return groupId;
	}

	public Long getUserId() {
		return userId;
	}

	public GroupRole getRole() {
		return role;
	}

	public Instant getJoinedAt() {
		return joinedAt;
	}
}
