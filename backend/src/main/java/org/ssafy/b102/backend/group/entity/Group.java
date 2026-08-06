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
import org.ssafy.b102.backend.global.common.entity.BaseTimeEntity;

/**
 * 길드.
 *
 * <p>{@code owner_user_id}는 {@code users}를 논리 참조한다. 참가자 테이블과 동일하게
 * 사용자 도메인 제약은 걸지 않고 컬럼만 유지한다. 테이블명은 SQL 예약어 {@code GROUPS}를
 * 피하려고 {@code groups_}로 둔다.
 */
@Entity
@Table(
	name = "groups_",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_groups_group_code",
		columnNames = "group_code"
	)
)
public class Group extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false, length = 50)
	private String name;

	@Column(name = "description", length = 255)
	private String description;

	@Column(name = "group_code", nullable = false, length = 6)
	private String groupCode;

	@Column(name = "owner_user_id", nullable = false)
	private Long ownerUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "visibility", nullable = false, length = 20)
	private GroupVisibility visibility;

	@Column(name = "capacity", nullable = false)
	private int capacity;

	protected Group() {
	}

	private Group(
		String name,
		String description,
		String groupCode,
		Long ownerUserId,
		GroupVisibility visibility,
		int capacity
	) {
		this.name = name;
		this.description = description;
		this.groupCode = groupCode;
		this.ownerUserId = ownerUserId;
		this.visibility = visibility;
		this.capacity = capacity;
	}

	public static Group create(
		String name,
		String description,
		String groupCode,
		Long ownerUserId,
		GroupVisibility visibility,
		int capacity
	) {
		return new Group(
			name,
			description,
			groupCode,
			ownerUserId,
			visibility,
			capacity
		);
	}

	public boolean isOwner(Long userId) {
		return ownerUserId.equals(userId);
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getGroupCode() {
		return groupCode;
	}

	public Long getOwnerUserId() {
		return ownerUserId;
	}

	public GroupVisibility getVisibility() {
		return visibility;
	}

	public int getCapacity() {
		return capacity;
	}
}
