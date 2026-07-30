package org.ssafy.b102.backend.waitingroom.controller;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.global.common.response.ApiResponse;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.waitingroom.WaitingRoomSuccessCode;
import org.ssafy.b102.backend.waitingroom.dto.request.WaitingRoomCreateRequest;
import org.ssafy.b102.backend.waitingroom.dto.request.WaitingRoomJoinRequest;
import org.ssafy.b102.backend.waitingroom.dto.response.WaitingRoomCreateResponse;
import org.ssafy.b102.backend.waitingroom.dto.response.WaitingRoomJoinResponse;
import org.ssafy.b102.backend.waitingroom.service.WaitingRoomService;
import org.ssafy.b102.backend.waitingroom.websocket.WaitingRoomWebSocketService;

@RestController
@RequestMapping("/api/v1/waiting-rooms")
public class WaitingRoomController {

	private static final String GUEST_SESSION_HEADER = "X-Guest-Session-Id";

	private final WaitingRoomService waitingRoomService;
	private final WaitingRoomWebSocketService waitingRoomWebSocketService;

	@Autowired
	public WaitingRoomController(
		WaitingRoomService waitingRoomService,
		ObjectProvider<WaitingRoomWebSocketService> waitingRoomWebSocketServiceProvider
	) {
		this.waitingRoomService = waitingRoomService;
		this.waitingRoomWebSocketService =
			waitingRoomWebSocketServiceProvider.getIfAvailable();
	}

	WaitingRoomController(WaitingRoomService waitingRoomService) {
		this.waitingRoomService = waitingRoomService;
		this.waitingRoomWebSocketService = null;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<WaitingRoomCreateResponse>> createInviteRoom(
		@AuthenticationPrincipal AuthenticatedUser member,
		@RequestHeader(value = GUEST_SESSION_HEADER, required = false) UUID guestSessionId,
		@Valid @RequestBody WaitingRoomCreateRequest request
	) {
		WaitingRoomCreateResponse response =
			waitingRoomService.createInviteRoom(member, guestSessionId, request);

		return ResponseEntity.status(HttpStatus.CREATED).body(
			ApiResponse.success(
				WaitingRoomSuccessCode.WAITING_ROOM_CREATE_SUCCESS,
				response
			)
		);
	}

	@PostMapping("/join")
	public ResponseEntity<ApiResponse<WaitingRoomJoinResponse>> joinInviteRoom(
		@AuthenticationPrincipal AuthenticatedUser member,
		@RequestHeader(value = GUEST_SESSION_HEADER, required = false) UUID guestSessionId,
		@Valid @RequestBody WaitingRoomJoinRequest request
	) {
		WaitingRoomJoinResponse response =
			waitingRoomService.joinInviteRoom(member, guestSessionId, request);

		return ResponseEntity.ok(
			ApiResponse.success(
				WaitingRoomSuccessCode.WAITING_ROOM_JOIN_SUCCESS,
				response
			)
		);
	}

	@PostMapping("/{roomId}/leave")
	public ResponseEntity<ApiResponse<Void>> leave(
		@PathVariable UUID roomId,
		@AuthenticationPrincipal AuthenticatedUser member,
		@RequestHeader(value = GUEST_SESSION_HEADER, required = false) UUID guestSessionId
	) {
		if (waitingRoomWebSocketService == null) {
			waitingRoomService.leave(roomId, member, guestSessionId);
		} else {
			waitingRoomWebSocketService.leaveFromRest(
				roomId,
				member,
				guestSessionId
			);
		}

		return ResponseEntity.ok(
			ApiResponse.success(
				WaitingRoomSuccessCode.WAITING_ROOM_LEAVE_SUCCESS,
				null
			)
		);
	}
}
