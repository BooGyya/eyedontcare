package org.ssafy.b102.backend.waitingroom.service;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.game.service.GameService;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.CommonErrorCode;
import org.ssafy.b102.backend.global.openvidu.LiveKitTokenService;
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
import org.ssafy.b102.backend.waitingroom.repository.LeaveRandomRoomCommand;
import org.ssafy.b102.backend.waitingroom.repository.RandomRoomLeaveResult;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomMetadata;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomSnapshot;
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
	private final LiveKitTokenService liveKitTokenService;

	@Autowired
	public WaitingRoomService(
		GameService gameService,
		WaitingRoomParticipantResolver participantResolver,
		WaitingRoomStore waitingRoomStore,
		WaitingRoomProperties properties,
		InviteCodeGenerator inviteCodeGenerator,
		RoomIdGenerator roomIdGenerator,
		LiveKitTokenService liveKitTokenService
	) {
		this(
			gameService,
			participantResolver,
			waitingRoomStore,
			properties,
			inviteCodeGenerator,
			roomIdGenerator,
			Clock.systemUTC(),
			liveKitTokenService
		);
	}

	WaitingRoomService(
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
			Clock.systemUTC(),
			null
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
		this(
			gameService,
			participantResolver,
			waitingRoomStore,
			properties,
			inviteCodeGenerator,
			roomIdGenerator,
			clock,
			null
		);
	}

	WaitingRoomService(
		GameService gameService,
		WaitingRoomParticipantResolver participantResolver,
		WaitingRoomStore waitingRoomStore,
		WaitingRoomProperties properties,
		InviteCodeGenerator inviteCodeGenerator,
		RoomIdGenerator roomIdGenerator,
		Clock clock,
		LiveKitTokenService liveKitTokenService
	) {
		this.gameService = gameService;
		this.participantResolver = participantResolver;
		this.waitingRoomStore = waitingRoomStore;
		this.properties = properties;
		this.inviteCodeGenerator = inviteCodeGenerator;
		this.roomIdGenerator = roomIdGenerator;
		this.clock = clock;
		this.liveKitTokenService = liveKitTokenService;
	}

	private String mediaUrl() {
		return liveKitTokenService == null ? null : liveKitTokenService.url();
	}

	private String issueMediaToken(
		String participantKey,
		String displayName,
		UUID roomId
	) {
		return liveKitTokenService == null
			? null
			: liveKitTokenService.issueToken(
				participantKey,
				displayName,
				roomId.toString()
			);
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
				return WaitingRoomCreateResponse.of(
					room,
					participant,
					identity,
					mediaUrl(),
					issueMediaToken(
						participant.participantKey(),
						participant.displayName(),
						room.roomId()
					)
				);
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
				yield WaitingRoomJoinResponse.of(
					result.snapshot(),
					identity,
					mediaUrl(),
					issueMediaToken(
						identity.participantKey(),
						identity.displayName(),
						result.snapshot().room().roomId()
					)
				);
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

	public WaitingRoomLeaveOutcome leave(
		UUID roomId,
		AuthenticatedUser member,
		UUID guestSessionId
	) {
		WaitingRoomMetadata metadata = findRoomMetadata(roomId);
		ResolvedWaitingRoomParticipant identity =
			participantResolver.resolveExisting(member, guestSessionId);

		return leaveWithOutcome(roomId, identity.participantKey(), metadata);
	}

	public LeaveWaitingRoomResult leaveByParticipantKey(
		UUID roomId,
		String participantKey
	) {
		WaitingRoomMetadata metadata = findRoomMetadata(roomId);
		if (metadata.roomType() == RoomType.RANDOM) {
			throw new BusinessException(
				WaitingRoomErrorCode.STATE_CHANGE_NOT_ALLOWED
			);
		}
		return leaveInviteByParticipantKey(roomId, participantKey, metadata);
	}

	public WaitingRoomLeaveOutcome leaveWithOutcomeByParticipantKey(
		UUID roomId,
		String participantKey
	) {
		return leaveWithOutcome(
			roomId,
			participantKey,
			findRoomMetadata(roomId)
		);
	}

	public WaitingRoomSnapshot findSnapshot(UUID roomId) {
		WaitingRoomSnapshot snapshot = waitingRoomStore.findSnapshot(roomId)
			.orElseThrow(() ->
				new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND));
		validateSnapshot(snapshot);
		return snapshot;
	}

	private void validateSnapshot(WaitingRoomSnapshot snapshot) {
		boolean invalidInvite =
			snapshot.room().roomType() == RoomType.INVITE &&
			(snapshot.room().roomCode() == null ||
				snapshot.room().roomCode().length() != 4 ||
				!snapshot.room().roomCode().chars().allMatch(Character::isDigit));
		boolean invalidRandom =
			snapshot.room().roomType() == RoomType.RANDOM &&
			(snapshot.room().roomCode() != null ||
				snapshot.participants().size() != 2 ||
				snapshot.participants().stream().anyMatch(participant ->
					participant.roomRole() != RoomRole.PLAYER));
		if (invalidInvite || invalidRandom || snapshot.participants().isEmpty()) {
			throw new BusinessException(
				WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE
			);
		}

		Set<String> participantKeys = new HashSet<>();
		Set<Integer> slots = new HashSet<>();
		for (WaitingRoomParticipant participant : snapshot.participants()) {
			if (
				participant.participantKey().isBlank() ||
				participant.displayName().isBlank() ||
				participant.slotNo() < 1 ||
				participant.slotNo() > properties.maxParticipants() ||
				!participantKeys.add(participant.participantKey()) ||
				!slots.add(participant.slotNo())
			) {
				throw new BusinessException(
					WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE
				);
			}
		}
	}

	private WaitingRoomMetadata findRoomMetadata(UUID roomId) {
		return waitingRoomStore.findRoomMetadata(roomId)
			.orElseThrow(() ->
				new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND));
	}

	private WaitingRoomLeaveOutcome leaveWithOutcome(
		UUID roomId,
		String participantKey,
		WaitingRoomMetadata metadata
	) {
		if (metadata.roomType() == RoomType.RANDOM) {
			return WaitingRoomLeaveOutcome.random(
				leaveRandomByParticipantKey(roomId, participantKey)
			);
		}
		return WaitingRoomLeaveOutcome.invite(
			roomId,
			participantKey,
			leaveInviteByParticipantKey(roomId, participantKey, metadata)
		);
	}

	private RandomRoomLeaveResult leaveRandomByParticipantKey(
		UUID roomId,
		String participantKey
	) {
		RandomRoomLeaveResult result = waitingRoomStore.leaveRandomRoomAtomically(
			new LeaveRandomRoomCommand(
				roomId,
				participantKey,
				properties.closedTtl()
			)
		);
		return switch (result.status()) {
			case CLOSED_NOW, ALREADY_CLOSED -> result;
			case NOT_JOINABLE ->
				throw new BusinessException(
					WaitingRoomErrorCode.WAITING_ROOM_NOT_JOINABLE
				);
			case ROOM_NOT_FOUND ->
				throw new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND);
			case PARTICIPANT_NOT_FOUND ->
				throw new BusinessException(WaitingRoomErrorCode.PARTICIPANT_NOT_FOUND);
			case CORRUPTED ->
				throw new BusinessException(
					WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE
				);
		};
	}

	private LeaveWaitingRoomResult leaveInviteByParticipantKey(
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
				return result;
			}
			case NOT_JOINABLE ->
				throw new BusinessException(
					WaitingRoomErrorCode.WAITING_ROOM_NOT_JOINABLE
				);
			case ROOM_NOT_FOUND ->
				throw new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND);
			case PARTICIPANT_NOT_FOUND ->
				throw new BusinessException(WaitingRoomErrorCode.PARTICIPANT_NOT_FOUND);
			case CORRUPTED ->
				throw new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE);
		}
		throw new IllegalStateException("Unreachable leave result");
	}

	private GameName parseGameName(String rawGameName) {
		try {
			return GameName.valueOf(rawGameName);
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new BusinessException(WaitingRoomErrorCode.INVALID_GAME_NAME);
		}
	}
}
