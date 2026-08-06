package org.ssafy.b102.backend.group.service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.group.dto.response.GroupCommentResponse;
import org.ssafy.b102.backend.group.dto.response.GroupPostListResponse;
import org.ssafy.b102.backend.group.dto.response.GroupPostResponse;
import org.ssafy.b102.backend.group.entity.Group;
import org.ssafy.b102.backend.group.entity.GroupComment;
import org.ssafy.b102.backend.group.entity.GroupPost;
import org.ssafy.b102.backend.group.entity.GroupVisibility;
import org.ssafy.b102.backend.group.exception.GroupErrorCode;
import org.ssafy.b102.backend.group.repository.GroupCommentRepository;
import org.ssafy.b102.backend.group.repository.GroupMemberRepository;
import org.ssafy.b102.backend.group.repository.GroupPostRepository;
import org.ssafy.b102.backend.group.repository.GroupRepository;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.repository.UserRepository;

/**
 * 길드 후기 게시판(글·댓글) 서비스.
 *
 * <p>조회는 인증된 회원이면 가능하고, 작성(글·댓글)은 해당 길드 가입자만 가능하다.
 * 댓글 수정·삭제는 그중에서도 작성자 본인만 할 수 있다. 작성자 닉네임은 users에서 조회해
 * 채우며, 방장이 쓴 글은 {@code isLeader}로, 요청자 본인이 쓴 댓글은 {@code mine}으로 표시한다.
 * 글자 수 상한은 요청 DTO의 {@code @Size}로 검증해 프론트 우회 요청까지 막는다.
 */
@Service
public class GroupBoardService {

	private final GroupRepository groupRepository;
	private final GroupMemberRepository groupMemberRepository;
	private final GroupPostRepository groupPostRepository;
	private final GroupCommentRepository groupCommentRepository;
	private final UserRepository userRepository;

	public GroupBoardService(
		GroupRepository groupRepository,
		GroupMemberRepository groupMemberRepository,
		GroupPostRepository groupPostRepository,
		GroupCommentRepository groupCommentRepository,
		UserRepository userRepository
	) {
		this.groupRepository = groupRepository;
		this.groupMemberRepository = groupMemberRepository;
		this.groupPostRepository = groupPostRepository;
		this.groupCommentRepository = groupCommentRepository;
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public GroupPostListResponse getPosts(Long userId, Long groupId) {
		Group group = findGroupOrThrow(groupId);
		requireViewable(group, userId);

		List<GroupPost> posts =
			groupPostRepository.findByGroupIdOrderByCreatedAtDesc(groupId);
		List<Long> postIds = posts.stream().map(GroupPost::getId).toList();

		List<GroupComment> comments = postIds.isEmpty()
			? List.of()
			: groupCommentRepository.findByPostIdInOrderByCreatedAtAsc(postIds);

		Map<Long, String> nicknames = nicknamesOf(authorIds(posts, comments));
		Map<Long, List<GroupComment>> commentsByPost = comments.stream()
			.collect(Collectors.groupingBy(GroupComment::getPostId));

		Long ownerUserId = group.getOwnerUserId();
		List<GroupPostResponse> items = posts.stream()
			.map(post -> new GroupPostResponse(
				post.getId(),
				nicknames.get(post.getAuthorUserId()),
				post.getAuthorUserId().equals(ownerUserId),
				post.getContent(),
				post.getCreatedAt(),
				commentsByPost
					.getOrDefault(post.getId(), List.of())
					.stream()
					.map(comment -> new GroupCommentResponse(
						comment.getId(),
						nicknames.get(comment.getAuthorUserId()),
						comment.getContent(),
						comment.getCreatedAt(),
						comment.getAuthorUserId().equals(userId)
					))
					.toList()
			))
			.toList();

		return new GroupPostListResponse(items);
	}

	@Transactional
	public GroupPostResponse createPost(
		Long userId,
		Long groupId,
		String content
	) {
		Group group = findGroupOrThrow(groupId);
		requireMember(groupId, userId);

		GroupPost saved =
			groupPostRepository.save(GroupPost.create(groupId, userId, content));

		return new GroupPostResponse(
			saved.getId(),
			nickname(userId),
			group.isOwner(userId),
			saved.getContent(),
			saved.getCreatedAt(),
			List.of()
		);
	}

	@Transactional
	public GroupCommentResponse createComment(
		Long userId,
		Long groupId,
		Long postId,
		String content
	) {
		findGroupOrThrow(groupId);
		requireMember(groupId, userId);
		findPostOrThrow(postId, groupId);

		GroupComment saved = groupCommentRepository.save(
			GroupComment.create(postId, userId, content));

		return new GroupCommentResponse(
			saved.getId(),
			nickname(userId),
			saved.getContent(),
			saved.getCreatedAt(),
			true
		);
	}

	/**
	 * 댓글을 수정한다. 길드원이면서 댓글 작성자 본인만 가능하다.
	 */
	@Transactional
	public GroupCommentResponse updateComment(
		Long userId,
		Long groupId,
		Long postId,
		Long commentId,
		String content
	) {
		findGroupOrThrow(groupId);
		requireMember(groupId, userId);
		GroupPost post = findPostOrThrow(postId, groupId);
		GroupComment comment = findCommentOrThrow(commentId, post.getId());
		requireCommentAuthor(comment, userId);

		comment.updateContent(content);

		return new GroupCommentResponse(
			comment.getId(),
			nickname(userId),
			comment.getContent(),
			comment.getCreatedAt(),
			true
		);
	}

	/**
	 * 댓글을 삭제한다. 길드원이면서 댓글 작성자 본인만 가능하다.
	 */
	@Transactional
	public void deleteComment(
		Long userId,
		Long groupId,
		Long postId,
		Long commentId
	) {
		findGroupOrThrow(groupId);
		requireMember(groupId, userId);
		GroupPost post = findPostOrThrow(postId, groupId);
		GroupComment comment = findCommentOrThrow(commentId, post.getId());
		requireCommentAuthor(comment, userId);

		groupCommentRepository.delete(comment);
	}

	private Group findGroupOrThrow(Long groupId) {
		return groupRepository.findById(groupId)
			.orElseThrow(() ->
				new BusinessException(GroupErrorCode.GROUP_NOT_FOUND));
	}

	private GroupPost findPostOrThrow(Long postId, Long groupId) {
		GroupPost post = groupPostRepository.findById(postId)
			.orElseThrow(() ->
				new BusinessException(GroupErrorCode.POST_NOT_FOUND));
		if (!post.getGroupId().equals(groupId)) {
			throw new BusinessException(GroupErrorCode.POST_NOT_FOUND);
		}
		return post;
	}

	private GroupComment findCommentOrThrow(Long commentId, Long postId) {
		GroupComment comment = groupCommentRepository.findById(commentId)
			.orElseThrow(() ->
				new BusinessException(GroupErrorCode.COMMENT_NOT_FOUND));
		if (!comment.getPostId().equals(postId)) {
			throw new BusinessException(GroupErrorCode.COMMENT_NOT_FOUND);
		}
		return comment;
	}

	private void requireCommentAuthor(GroupComment comment, Long userId) {
		if (!comment.getAuthorUserId().equals(userId)) {
			throw new BusinessException(GroupErrorCode.COMMENT_FORBIDDEN);
		}
	}

	private void requireMember(Long groupId, Long userId) {
		if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
			throw new BusinessException(GroupErrorCode.NOT_A_MEMBER);
		}
	}

	/** 비공개 길드 게시판은 길드원만 조회할 수 있다(공개는 비회원도 조회 가능). */
	private void requireViewable(Group group, Long userId) {
		if (group.getVisibility() != GroupVisibility.PUBLIC
			&& !groupMemberRepository.existsByGroupIdAndUserId(
				group.getId(), userId)) {
			throw new BusinessException(GroupErrorCode.PRIVATE_GROUP_MEMBER_ONLY);
		}
	}

	private Set<Long> authorIds(
		List<GroupPost> posts,
		List<GroupComment> comments
	) {
		Set<Long> ids = new LinkedHashSet<>();
		posts.forEach(post -> ids.add(post.getAuthorUserId()));
		comments.forEach(comment -> ids.add(comment.getAuthorUserId()));
		return ids;
	}

	private Map<Long, String> nicknamesOf(Collection<Long> userIds) {
		return userRepository.findAllById(userIds).stream()
			.collect(Collectors.toMap(User::getId, User::getNickname));
	}

	private String nickname(Long userId) {
		return userRepository.findById(userId)
			.map(User::getNickname)
			.orElse(null);
	}
}
