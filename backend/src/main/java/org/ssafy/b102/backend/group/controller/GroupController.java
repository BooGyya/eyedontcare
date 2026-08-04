package org.ssafy.b102.backend.group.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.global.common.response.ApiResponse;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.group.GroupSuccessCode;
import org.ssafy.b102.backend.group.dto.request.GroupCreateRequest;
import org.ssafy.b102.backend.group.dto.request.GroupJoinRequest;
import org.ssafy.b102.backend.group.dto.response.GroupDetailResponse;
import org.ssafy.b102.backend.group.dto.response.GroupListResponse;
import org.ssafy.b102.backend.group.dto.response.GroupResponse;
import org.ssafy.b102.backend.group.dto.response.MyGroupListResponse;
import org.ssafy.b102.backend.group.service.GroupService;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

	private final GroupService groupService;

	public GroupController(GroupService groupService) {
		this.groupService = groupService;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<GroupResponse>> create(
		@AuthenticationPrincipal AuthenticatedUser member,
		@Valid @RequestBody GroupCreateRequest request
	) {
		GroupResponse response =
			groupService.create(member.userId(), request);

		return ResponseEntity
			.status(HttpStatus.CREATED)
			.body(ApiResponse.success(
				GroupSuccessCode.GROUP_CREATE_SUCCESS,
				response
			));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<GroupListResponse>> getGroups(
		@AuthenticationPrincipal AuthenticatedUser member,
		@RequestParam(required = false) String keyword,
		@RequestParam(defaultValue = "1") int page,
		@RequestParam(defaultValue = "20") int size
	) {
		GroupListResponse response =
			groupService.getGroups(member.userId(), keyword, page, size);

		return ResponseEntity.ok(ApiResponse.success(
			GroupSuccessCode.GROUP_LIST_FOUND,
			response
		));
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<MyGroupListResponse>> getMyGroups(
		@AuthenticationPrincipal AuthenticatedUser member
	) {
		MyGroupListResponse response =
			groupService.getMyGroups(member.userId());

		return ResponseEntity.ok(ApiResponse.success(
			GroupSuccessCode.MY_GROUP_LIST_FOUND,
			response
		));
	}

	@GetMapping("/{groupId}")
	public ResponseEntity<ApiResponse<GroupDetailResponse>> getGroup(
		@AuthenticationPrincipal AuthenticatedUser member,
		@PathVariable Long groupId
	) {
		GroupDetailResponse response =
			groupService.getGroup(member.userId(), groupId);

		return ResponseEntity.ok(ApiResponse.success(
			GroupSuccessCode.GROUP_DETAIL_FOUND,
			response
		));
	}

	@PostMapping("/join")
	public ResponseEntity<ApiResponse<GroupResponse>> join(
		@AuthenticationPrincipal AuthenticatedUser member,
		@Valid @RequestBody GroupJoinRequest request
	) {
		GroupResponse response =
			groupService.join(member.userId(), request.groupCode());

		return ResponseEntity.ok(ApiResponse.success(
			GroupSuccessCode.GROUP_JOIN_SUCCESS,
			response
		));
	}

	@PostMapping("/{groupId}/join")
	public ResponseEntity<ApiResponse<GroupResponse>> joinById(
		@AuthenticationPrincipal AuthenticatedUser member,
		@PathVariable Long groupId
	) {
		GroupResponse response =
			groupService.joinById(member.userId(), groupId);

		return ResponseEntity.ok(ApiResponse.success(
			GroupSuccessCode.GROUP_JOIN_SUCCESS,
			response
		));
	}

	@PostMapping("/{groupId}/leave")
	public ResponseEntity<ApiResponse<Void>> leave(
		@AuthenticationPrincipal AuthenticatedUser member,
		@PathVariable Long groupId
	) {
		groupService.leave(member.userId(), groupId);

		return ResponseEntity.ok(ApiResponse.success(
			GroupSuccessCode.GROUP_LEAVE_SUCCESS,
			null
		));
	}

	@DeleteMapping("/{groupId}")
	public ResponseEntity<ApiResponse<Void>> delete(
		@AuthenticationPrincipal AuthenticatedUser member,
		@PathVariable Long groupId
	) {
		groupService.delete(member.userId(), groupId);

		return ResponseEntity.ok(ApiResponse.success(
			GroupSuccessCode.GROUP_DELETE_SUCCESS,
			null
		));
	}

	@DeleteMapping("/{groupId}/members/{userId}")
	public ResponseEntity<ApiResponse<Void>> kick(
		@AuthenticationPrincipal AuthenticatedUser member,
		@PathVariable Long groupId,
		@PathVariable Long userId
	) {
		groupService.kick(member.userId(), groupId, userId);

		return ResponseEntity.ok(ApiResponse.success(
			GroupSuccessCode.GROUP_MEMBER_KICK_SUCCESS,
			null
		));
	}
}
