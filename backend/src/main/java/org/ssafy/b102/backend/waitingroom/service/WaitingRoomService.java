package org.ssafy.b102.backend.waitingroom.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.game.service.GameService;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.CommonErrorCode;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.waitingroom.config.WaitingRoomProperties;
import org.ssafy.b102.backend.waitingroom.dto.request.WaitingRoomCreateRequest;
import org.ssafy.b102.backend.waitingroom.dto.request.WaitingRoomJoinRequest;
import org.ssafy.b102.backend.waitingroom.dto.response.WaitingRoomCreateResponse;
import org.ssafy.b102.backend.waitingroom.dto.response.WaitingRoomJoinResponse;
import org.ssafy.b102.backend.waitingroom.entity.CalibrationStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomRole;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
import org.ssafy.b102.backend.waitingroom.repository.CreateInviteRoomCommand;
import org.ssafy.b102.backend.waitingroom.repository.CreateInviteRoomResult;
import org.ssafy.b102.backend.waitingroom.repository.JoinInviteRoomCommand;
import org.ssafy.b102.backend.waitingroom.repository.JoinInviteRoomResult;
import org.ssafy.b102.backend.waitingroom.repository.LeaveWaitingRoomCommand;
import org.ssafy.b102.backend.waitingroom.repository.LeaveWaitingRoomResult;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomMetadata;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomStore;
import org.ssafy.b102.backend.waitingroom.support.InviteCodeGenerator;
import org.ssafy.b102.backend.waitingroom.support.ResolvedWaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.support.RoomIdGenerator;
import org.ssafy.b102.backend.waitingroom.support.WaitingRoomParticipantResolver;

@Service
public class WaitingRoomService {

	private final GameService gameService;
	private final WaitingRoomParticipantResolver participantResolver;
	private final WaitingRoomStore waitingRoomStore;
	private final WaitingRoomProperties properties;
	private final InviteCodeGenerator inviteCodeGenerator;
	private final RoomIdGenerator roomIdGenerator;
	private final Clock clock;

	@Autowired
	public WaitingRoomService(
		GameService gameService,
		WaitingRoomParticipantResolver participantResolver,
		WaitingRoomStore waitingRoomStore,
		WaitingRoomProperties properties,
		InviteCodeGenerator inviteCodeGenerator,
		RoomIdGenerator roomIdGenerator
	) {
		this(
			gameService,
			participantResolver,
			waitingRoomStore,
			properties,
			inviteCodeGenerator,
			roomIdGenerator,
			Clock.systemUTC()
		);
	}

	WaitingRoomService(
		GameService gameService,
		WaitingRoomParticipantResolver participantResolver,
		WaitingRoomStore waitingRoomStore,
		WaitingRoomProperties properties,
		InviteCodeGenerator inviteCodeGenerator,
		RoomIdGenerator roomIdGenerator,
		Clock clock
	) {
		this.gameService = gameService;
		this.participantResolver = participantResolver;
		this.waitingRoomStore = waitingRoomStore;
		this.properties = properties;
		this.inviteCodeGenerator = inviteCodeGenerator;
		this.roomIdGenerator = roomIdGenerator;
		this.clock = clock;
	}

	public WaitingRoomCreateResponse createInviteRoom(
		AuthenticatedUser member,
		UUID guestSessionId,
		WaitingRoomCreateRequest request
	) {
		if (request.hasUnknownFields()) {
			throw new BusinessException(CommonErrorCode.MALFORMED_JSON);
		}

		GameName gameName = parseGameName(request.getGameName());
		if (!gameService.supportsPlayMode(gameName, PlayMode.INVITE)) {
			throw new BusinessException(WaitingRoomErrorCode.INVALID_GAME_NAME);
		}

		ResolvedWaitingRoomParticipant identity =
			participantResolver.resolve(member, guestSessionId);
		UUID roomId = roomIdGenerator.generate();
		Instant now = clock.instant();
		WaitingRoomParticipant participant = new WaitingRoomParticipant(
			identity.participantKey(),
			identity.displayName(),
			RoomRole.HOST,
			1,
			false,
			CalibrationStatus.PENDING,
			now
		);

		for (int attempt = 0; attempt < properties.inviteCodeMaxAttempts(); attempt++) {
			WaitingRoom room = new WaitingRoom(
				roomId,
				RoomType.INVITE,
				gameName,
				inviteCodeGenerator.generate(),
				RoomStatus.WAITING,
				now
			);
			CreateInviteRoomResult result = waitingRoomStore.createInviteRoomAtomically(
				new CreateInviteRoomCommand(room, participant, properties.activeTtl())
			);
			if (result == CreateInviteRoomResult.CREATED) {
				return WaitingRoomCreateResponse.of(room, participant, identity);
			}
		}

		throw new BusinessException(WaitingRoomErrorCode.INVITE_CODE_GENERATION_FAILED);
	}

	public WaitingRoomJoinResponse joinInviteRoom(
		AuthenticatedUser member,
		UUID guestSessionId,
		WaitingRoomJoinRequest request
	) {
		if (request.hasUnknownFields()) {
			throw new BusinessException(CommonErrorCode.MALFORMED_JSON);
		}

		UUID roomId = waitingRoomStore.findRoomIdByInviteCode(request.getRoomCode())
			.orElseThrow(() ->
				new BusinessException(WaitingRoomErrorCode.INVALID_INVITE_CODE));
		ResolvedWaitingRoomParticipant identity =
			participantResolver.resolve(member, guestSessionId);
		JoinInviteRoomResult result = waitingRoomStore.joinInviteRoomAtomically(
			new JoinInviteRoomCommand(
				roomId,
				request.getRoomCode(),
				identity.participantKey(),
				identity.displayName(),
				clock.instant(),
				properties.maxParticipants(),
				properties.activeTtl()
			)
		);

		return switch (result.status()) {
			case JOINED -> {
				if (result.snapshot() == null) {
					throw new BusinessException(
						WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE
					);
				}
				yield WaitingRoomJoinResponse.of(result.snapshot(), identity);
			}
			case INVALID_INVITE_CODE ->
				throw new BusinessException(WaitingRoomErrorCode.INVALID_INVITE_CODE);
			case NOT_JOINABLE ->
				throw new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_NOT_JOINABLE);
			case ALREADY_JOINED ->
				throw new BusinessException(WaitingRoomErrorCode.PARTICIPANT_ALREADY_JOINED);
			case FULL ->
				throw new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_FULL);
			case CORRUPTED ->
				throw new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE);
		};
	}

	public void leave(
		UUID roomId,
		AuthenticatedUser member,
		UUID guestSessionId
	) {
		WaitingRoomMetadata metadata = findRoomMetadata(roomId);
		ResolvedWaitingRoomParticipant identity =
			participantResolver.resolveExisting(member, guestSessionId);

		leaveByParticipantKey(roomId, identity.participantKey(), metadata);
	}

	public void leaveByParticipantKey(UUID roomId, String participantKey) {
		leaveByParticipantKey(roomId, participantKey, findRoomMetadata(roomId));
	}

	private WaitingRoomMetadata findRoomMetadata(UUID roomId) {
		return waitingRoomStore.findRoomMetadata(roomId)
			.orElseThrow(() ->
				new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND));
	}

	private void leaveByParticipantKey(
		UUID roomId,
		String participantKey,
		WaitingRoomMetadata metadata
	) {
		LeaveWaitingRoomResult result = waitingRoomStore.leaveAtomically(
			new LeaveWaitingRoomCommand(
				roomId,
				metadata.roomCode(),
				participantKey,
				properties.maxParticipants(),
				properties.activeTtl(),
				properties.closedTtl()
			)
		);

		switch (result) {
			case LEFT, ROOM_CLOSED, ALREADY_CLOSED -> {
				return;
			}
			case ROOM_NOT_FOUND ->
				throw new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND);
			case PARTICIPANT_NOT_FOUND ->
				throw new BusinessException(WaitingRoomErrorCode.PARTICIPANT_NOT_FOUND);
			case CORRUPTED ->
				throw new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE);
		}
	}

	private GameName parseGameName(String rawGameName) {
		try {
			return GameName.valueOf(rawGameName);
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new BusinessException(WaitingRoomErrorCode.INVALID_GAME_NAME);
		}
	}
}
