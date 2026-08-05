package org.ssafy.b102.backend.group.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.ssafy.b102.backend.global.common.entity.BaseTimeEntity;

/**
 * 소모임 게임 후기 글.
 *
 * <p>{@code group_id}는 {@code groups_}를, {@code author_user_id}는 {@code users}를 논리 참조한다.
 * 다른 도메인처럼 사용자·소모임 제약은 컬럼으로만 유지한다. 작성 시각은 {@link BaseTimeEntity}가 관리한다.
 */
@Entity
@Table(name = "group_posts")
public class GroupPost extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "group_id", nullable = false)
	private Long groupId;

	@Column(name = "author_user_id", nullable = false)
	private Long authorUserId;

	@Column(name = "content", nullable = false, length = 500)
	private String content;

	protected GroupPost() {
	}

	private GroupPost(Long groupId, Long authorUserId, String content) {
		this.groupId = groupId;
		this.authorUserId = authorUserId;
		this.content = content;
	}

	public static GroupPost create(
		Long groupId,
		Long authorUserId,
		String content
	) {
		return new GroupPost(groupId, authorUserId, content);
	}

	public Long getId() {
		return id;
	}

	public Long getGroupId() {
		return groupId;
	}

	public Long getAuthorUserId() {
		return authorUserId;
	}

	public String getContent() {
		return content;
	}
}
