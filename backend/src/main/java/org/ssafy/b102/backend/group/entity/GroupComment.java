package org.ssafy.b102.backend.group.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.ssafy.b102.backend.global.common.entity.BaseTimeEntity;

/**
 * 소모임 후기 글에 달린 댓글.
 *
 * <p>{@code post_id}는 {@code group_posts}를, {@code author_user_id}는 {@code users}를 논리 참조한다.
 * 작성 시각은 {@link BaseTimeEntity}가 관리한다.
 */
@Entity
@Table(name = "group_comments")
public class GroupComment extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "post_id", nullable = false)
	private Long postId;

	@Column(name = "author_user_id", nullable = false)
	private Long authorUserId;

	@Column(name = "content", nullable = false, length = 200)
	private String content;

	protected GroupComment() {
	}

	private GroupComment(Long postId, Long authorUserId, String content) {
		this.postId = postId;
		this.authorUserId = authorUserId;
		this.content = content;
	}

	public static GroupComment create(
		Long postId,
		Long authorUserId,
		String content
	) {
		return new GroupComment(postId, authorUserId, content);
	}

	public Long getId() {
		return id;
	}

	public Long getPostId() {
		return postId;
	}

	public Long getAuthorUserId() {
		return authorUserId;
	}

	public String getContent() {
		return content;
	}

	public void updateContent(String content) {
		this.content = content;
	}
}
