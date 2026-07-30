package org.ssafy.b102.backend.waitingroom.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Repository;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
import org.ssafy.b102.backend.waitingroom.repository.JoinInviteRoomResult.Status;
import org.ssafy.b102.backend.waitingroom.repository.model.StoredParticipant;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class RedisWaitingRoomStore implements WaitingRoomStore {

	private static final String DOMAIN = "waiting-room";
	private static final Long CREATED_RESULT = 1L;
	private static final Long CONFLICT_RESULT = 0L;
	private static final DefaultRedisScript<Long> CREATE_SCRIPT = script(
		"redis/waiting-room/create-invite-room.lua"
	);
	private static final DefaultRedisScript<Long> CLEANUP_SCRIPT = script(
		"redis/waiting-room/cleanup-failed-create.lua"
	);
	private static final DefaultRedisScript<String> JOIN_SCRIPT = stringScript(
		"redis/waiting-room/join-invite-room.lua"
	);
	private static final DefaultRedisScript<String> LEAVE_SCRIPT = stringScript(
		"redis/waiting-room/leave-waiting-room.lua"
	);
	private static final DefaultRedisScript<String> READ_SCRIPT = stringScript(
		"redis/waiting-room/read-waiting-room.lua"
	);
	private static final DefaultRedisScript<String> UPDATE_CALIBRATION_SCRIPT =
		stringScript("redis/waiting-room/update-calibration.lua");
	private static final DefaultRedisScript<String> UPDATE_READY_SCRIPT =
		stringScript("redis/waiting-room/update-ready.lua");
	private static final DefaultRedisScript<String> START_GAME_SCRIPT =
		stringScript("redis/waiting-room/start-invite-game.lua");
	private static final DefaultRedisScript<String> COMPLETE_COUNTDOWN_SCRIPT =
		stringScript("redis/waiting-room/complete-countdown.lua");
	private static final DefaultRedisScript<String> ROLLBACK_COUNTDOWN_SCRIPT =
		stringScript("redis/waiting-room/rollback-countdown.lua");

	private final StringRedisTemplate redisTemplate;
	private final RedisKeyBuilder redisKeyBuilder;
	private final JsonMapper jsonMapper;

	public RedisWaitingRoomStore(
		StringRedisTemplate redisTemplate,
		RedisKeyBuilder redisKeyBuilder,
		JsonMapper jsonMapper
	) {
		this.redisTemplate = redisTemplate;
		this.redisKeyBuilder = redisKeyBuilder;
		this.jsonMapper = jsonMapper;
	}

	@Override
	public CreateInviteRoomResult createInviteRoomAtomically(CreateInviteRoomCommand command) {
		WaitingRoom room = command.room();
		WaitingRoomParticipant participant = command.participant();
		List<String> keys = keys(room);

		try {
			String participantJson = jsonMapper.writeValueAsString(
				StoredParticipant.from(participant)
			);
			Long result = redisTemplate.execute(
				CREATE_SCRIPT,
				keys,
				room.roomId().toString(),
				room.roomType().name(),
				room.gameName().name(),
				room.roomCode(),
				room.roomStatus().name(),
				room.createdAt().toString(),
				participant.participantKey(),
				participantJson,
				Long.toString(command.ttl().toMillis())
			);

			if (CREATED_RESULT.equals(result)) {
				return CreateInviteRoomResult.CREATED;
			}
			if (CONFLICT_RESULT.equals(result)) {
				return CreateInviteRoomResult.INVITE_CODE_CONFLICT;
			}
			throw storeUnavailable();
		} catch (BusinessException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			cleanup(keys, room.roomId().toString());
			throw storeUnavailable();
		}
	}

	@Override
	public Optional<UUID> findRoomIdByInviteCode(String roomCode) {
		try {
			String roomId = redisTemplate.opsForValue().get(inviteCodeKey(roomCode));
			if (roomId == null) {
				return Optional.empty();
			}
			return Optional.of(UUID.fromString(roomId));
		} catch (RuntimeException exception) {
			throw storeUnavailable();
		}
	}

	@Override
	public JoinInviteRoomResult joinInviteRoomAtomically(JoinInviteRoomCommand command) {
		List<String> keys = List.of(
			inviteCodeKey(command.roomCode()),
			roomKey(command.roomId()),
			participantsKey(command.roomId())
		);

		try {
			String participantJson = jsonMapper.writeValueAsString(
				StoredParticipant.joining(command.displayName(), command.joinedAt())
			);
			String rawResult = redisTemplate.execute(
				JOIN_SCRIPT,
				keys,
				command.roomId().toString(),
				command.participantKey(),
				participantJson,
				command.roomCode(),
				Integer.toString(command.maxParticipants()),
				Long.toString(command.ttl().toMillis())
			);
			if (rawResult == null) {
				throw storeUnavailable();
			}

			return toJoinResult(jsonMapper.readValue(rawResult, JoinScriptResponse.class));
		} catch (BusinessException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw storeUnavailable();
		}
	}

	@Override
	public Optional<WaitingRoomMetadata> findRoomMetadata(UUID roomId) {
		try {
			Map<Object, Object> fields = redisTemplate.opsForHash().entries(roomKey(roomId));
			if (fields.isEmpty()) {
				return Optional.empty();
			}

			RoomType roomType = RoomType.valueOf(requiredField(fields, "roomType"));
			RoomStatus roomStatus = RoomStatus.valueOf(requiredField(fields, "roomStatus"));
			String roomCode = (String) fields.get("roomCode");
			if (roomType != RoomType.INVITE) {
				throw storeUnavailable();
			}
			if (roomCode == null || !roomCode.matches("[0-9]{4}")) {
				throw storeUnavailable();
			}
			requiredField(fields, "gameName");
			requiredField(fields, "createdAt");

			return Optional.of(
				new WaitingRoomMetadata(roomId, roomType, roomStatus, roomCode)
			);
		} catch (BusinessException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw storeUnavailable();
		}
	}

	@Override
	public Optional<WaitingRoomSnapshot> findSnapshot(UUID roomId) {
		try {
			String rawResult = redisTemplate.execute(
				READ_SCRIPT,
				List.of(roomKey(roomId), participantsKey(roomId)),
				roomId.toString()
			);
			if (rawResult == null) {
				throw storeUnavailable();
			}

			SnapshotScriptResponse response =
				jsonMapper.readValue(rawResult, SnapshotScriptResponse.class);
			if (response == null || response.status() == null) {
				throw storeUnavailable();
			}
			return switch (response.status()) {
				case "ROOM_NOT_FOUND" -> Optional.empty();
				case "CORRUPTED" -> throw storeUnavailable();
				case "FOUND" -> Optional.of(toSnapshot(response));
				default -> throw storeUnavailable();
			};
		} catch (BusinessException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw storeUnavailable();
		}
	}

	@Override
	public LeaveWaitingRoomResult leaveAtomically(LeaveWaitingRoomCommand command) {
		List<String> keys = List.of(
			roomKey(command.roomId()),
			participantsKey(command.roomId()),
			inviteCodeKey(command.roomCode())
		);

		try {
			String result = redisTemplate.execute(
				LEAVE_SCRIPT,
				keys,
				command.roomId().toString(),
				command.participantKey(),
				Long.toString(command.activeTtl().toMillis()),
				Long.toString(command.closedTtl().toMillis()),
				command.roomCode(),
				Integer.toString(command.maxParticipants())
			);
			if (result == null) {
				throw storeUnavailable();
			}
			return LeaveWaitingRoomResult.valueOf(result);
		} catch (BusinessException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw storeUnavailable();
		}
	}

	@Override
	public UpdateCalibrationResult updateCalibrationAtomically(
		UpdateCalibrationCommand command
	) {
		String result = executeString(
			UPDATE_CALIBRATION_SCRIPT,
			stateKeys(command.roomId(), command.roomCode()),
			command.roomId().toString(),
			command.roomCode(),
			command.participantKey(),
			command.calibrationStatus().name(),
			Long.toString(command.activeTtl().toMillis()),
			Integer.toString(command.maxParticipants())
		);
		try {
			return UpdateCalibrationResult.valueOf(result);
		} catch (IllegalArgumentException exception) {
			throw storeUnavailable();
		}
	}

	@Override
	public UpdateReadyResult updateReadyAtomically(UpdateReadyCommand command) {
		String result = executeString(
			UPDATE_READY_SCRIPT,
			stateKeys(command.roomId(), command.roomCode()),
			command.roomId().toString(),
			command.roomCode(),
			command.participantKey(),
			Boolean.toString(command.ready()),
			Long.toString(command.activeTtl().toMillis()),
			Integer.toString(command.maxParticipants())
		);
		try {
			return UpdateReadyResult.valueOf(result);
		} catch (IllegalArgumentException exception) {
			throw storeUnavailable();
		}
	}

	@Override
	public StartInviteGameResult startInviteGameAtomically(
		StartInviteGameCommand command
	) {
		String result = executeString(
			START_GAME_SCRIPT,
			stateKeys(command.roomId(), command.roomCode()),
			command.roomId().toString(),
			command.roomCode(),
			command.participantKey(),
			command.countdownId().toString(),
			command.countdownEndsAt().toString(),
			Integer.toString(command.maxParticipants()),
			Long.toString(command.activeTtl().toMillis())
		);
		try {
			StartScriptResponse response =
				jsonMapper.readValue(result, StartScriptResponse.class);
			StartInviteGameResult.Status status =
				StartInviteGameResult.Status.valueOf(response.status());
			return new StartInviteGameResult(
				status,
				response.countdownId(),
				response.countdownEndsAt()
			);
		} catch (RuntimeException exception) {
			throw storeUnavailable();
		}
	}

	@Override
	public CompleteCountdownResult completeCountdownAtomically(
		CompleteCountdownCommand command
	) {
		String result = executeString(
			COMPLETE_COUNTDOWN_SCRIPT,
			stateKeys(command.roomId(), command.roomCode()),
			command.roomId().toString(),
			command.roomCode(),
			command.countdownId().toString(),
			command.countdownEndsAt().toString(),
			Integer.toString(command.maxParticipants()),
			Long.toString(command.activeTtl().toMillis())
		);
		try {
			return CompleteCountdownResult.valueOf(result);
		} catch (IllegalArgumentException exception) {
			throw storeUnavailable();
		}
	}

	@Override
	public RollbackCountdownResult rollbackCountdownAtomically(
		RollbackCountdownCommand command
	) {
		String result = executeString(
			ROLLBACK_COUNTDOWN_SCRIPT,
			stateKeys(command.roomId(), command.roomCode()),
			command.roomId().toString(),
			command.roomCode(),
			command.countdownId().toString(),
			Long.toString(command.activeTtl().toMillis())
		);
		try {
			return RollbackCountdownResult.valueOf(result);
		} catch (IllegalArgumentException exception) {
			throw storeUnavailable();
		}
	}

	private List<String> keys(WaitingRoom room) {
		return List.of(
			roomKey(room.roomId()),
			participantsKey(room.roomId()),
			inviteCodeKey(room.roomCode())
		);
	}

	private JoinInviteRoomResult toJoinResult(JoinScriptResponse response) {
		Status status = Status.valueOf(response.status());
		if (status != Status.JOINED) {
			return JoinInviteRoomResult.of(status);
		}
		if (response.room() == null || response.participants() == null) {
			throw storeUnavailable();
		}

		return JoinInviteRoomResult.joined(
			toSnapshot(response.room(), response.participants())
		);
	}

	private WaitingRoomSnapshot toSnapshot(SnapshotScriptResponse response) {
		if (response.room() == null || response.participants() == null) {
			throw storeUnavailable();
		}
		return toSnapshot(response.room(), response.participants());
	}

	private WaitingRoomSnapshot toSnapshot(
		StoredRoom storedRoom,
		List<StoredParticipantEntry> storedParticipants
	) {
		WaitingRoom room = new WaitingRoom(
			UUID.fromString(storedRoom.roomId()),
			RoomType.valueOf(storedRoom.roomType()),
			GameName.valueOf(storedRoom.gameName()),
			storedRoom.roomCode(),
			RoomStatus.valueOf(storedRoom.roomStatus()),
			storedRoom.createdAt(),
			storedRoom.countdownId(),
			storedRoom.countdownEndsAt()
		);
		List<WaitingRoomParticipant> participants = storedParticipants.stream()
			.map(stored -> stored.participant().toParticipant(stored.participantKey()))
			.toList();

		return new WaitingRoomSnapshot(room, participants);
	}

	private String roomKey(UUID roomId) {
		return redisKeyBuilder.build(DOMAIN, "room", roomId.toString());
	}

	private String participantsKey(UUID roomId) {
		return redisKeyBuilder.build(DOMAIN, "participants", roomId.toString());
	}

	private String inviteCodeKey(String roomCode) {
		return redisKeyBuilder.build(DOMAIN, "invite-code", roomCode);
	}

	private List<String> stateKeys(UUID roomId, String roomCode) {
		return List.of(
			roomKey(roomId),
			participantsKey(roomId),
			inviteCodeKey(roomCode)
		);
	}

	private String executeString(
		DefaultRedisScript<String> script,
		List<String> keys,
		String... arguments
	) {
		try {
			String result = redisTemplate.execute(
				script,
				keys,
				(Object[]) arguments
			);
			if (result == null) {
				throw storeUnavailable();
			}
			return result;
		} catch (BusinessException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw storeUnavailable();
		}
	}

	private String requiredField(Map<Object, Object> fields, String name) {
		Object value = fields.get(name);
		if (!(value instanceof String text) || text.isBlank()) {
			throw storeUnavailable();
		}
		return text;
	}

	private void cleanup(List<String> keys, String roomId) {
		try {
			redisTemplate.execute(CLEANUP_SCRIPT, keys, roomId);
		} catch (RuntimeException ignored) {
			// 원래 Redis 오류를 보존한다. 정리는 best-effort다.
		}
	}

	private static DefaultRedisScript<Long> script(String path) {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>();
		script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
		script.setResultType(Long.class);
		return script;
	}

	private static DefaultRedisScript<String> stringScript(String path) {
		DefaultRedisScript<String> script = new DefaultRedisScript<>();
		script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
		script.setResultType(String.class);
		return script;
	}

	private static BusinessException storeUnavailable() {
		return new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE);
	}

	record JoinScriptResponse(
		String status,
		StoredRoom room,
		List<StoredParticipantEntry> participants
	) {
	}

	record SnapshotScriptResponse(
		String status,
		StoredRoom room,
		List<StoredParticipantEntry> participants
	) {
	}

	record StoredRoom(
		String roomId,
		String roomType,
		String gameName,
		String roomCode,
		String roomStatus,
		java.time.Instant createdAt,
		UUID countdownId,
		java.time.Instant countdownEndsAt
	) {
	}

	record StartScriptResponse(
		String status,
		UUID countdownId,
		java.time.Instant countdownEndsAt
	) {
	}

	record StoredParticipantEntry(
		String participantKey,
		StoredParticipant participant
	) {
	}
}
