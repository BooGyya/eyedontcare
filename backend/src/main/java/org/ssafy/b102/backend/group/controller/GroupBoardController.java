package org.ssafy.b102.backend.group.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.global.common.response.ApiResponse;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.group.GroupSuccessCode;
import org.ssafy.b102.backend.group.dto.request.GroupCommentCreateRequest;
import org.ssafy.b102.backend.group.dto.request.GroupPostCreateRequest;
import org.ssafy.b102.backend.group.dto.response.GroupCommentResponse;
import org.ssafy.b102.backend.group.dto.response.GroupPostListResponse;
import org.ssafy.b102.backend.group.dto.response.GroupPostResponse;
import org.ssafy.b102.backend.group.service.GroupBoardService;

/**
 * 소모임 후기 게시판(글·댓글) API. 조회는 회원 인증만, 작성은 해당 소모임 가입자만 가능하다.
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}")
public class GroupBoardController {

	private final GroupBoardService groupBoardService;

	public GroupBoardController(GroupBoardService groupBoardService) {
		this.groupBoardService = groupBoardService;
	}

	@GetMapping("/posts")
	public ResponseEntity<ApiResponse<GroupPostListResponse>> getPosts(
		@AuthenticationPrincipal AuthenticatedUser member,
		@PathVariable Long groupId
	) {
		GroupPostListResponse response =
			groupBoardService.getPosts(member.userId(), groupId);

		return ResponseEntity.ok(ApiResponse.success(
			GroupSuccessCode.GROUP_POST_LIST_FOUND,
			response
		));
	}

	@PostMapping("/posts")
	public ResponseEntity<ApiResponse<GroupPostResponse>> createPost(
		@AuthenticationPrincipal AuthenticatedUser member,
		@PathVariable Long groupId,
		@Valid @RequestBody GroupPostCreateRequest request
	) {
		GroupPostResponse response = groupBoardService.createPost(
			member.userId(),
			groupId,
			request.content()
		);

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ApiResponse.success(
				GroupSuccessCode.GROUP_POST_CREATE_SUCCESS,
				response
			));
	}

	@PostMapping("/posts/{postId}/comments")
	public ResponseEntity<ApiResponse<GroupCommentResponse>> createComment(
		@AuthenticationPrincipal AuthenticatedUser member,
		@PathVariable Long groupId,
		@PathVariable Long postId,
		@Valid @RequestBody GroupCommentCreateRequest request
	) {
		GroupCommentResponse response = groupBoardService.createComment(
			member.userId(),
			groupId,
			postId,
			request.content()
		);

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ApiResponse.success(
				GroupSuccessCode.GROUP_COMMENT_CREATE_SUCCESS,
				response
			));
	}
}
