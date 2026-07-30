package org.ssafy.b102.backend.waitingroom.repository;

import java.util.List;
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

		StoredRoom storedRoom = response.room();
		WaitingRoom room = new WaitingRoom(
			UUID.fromString(storedRoom.roomId()),
			RoomType.valueOf(storedRoom.roomType()),
			GameName.valueOf(storedRoom.gameName()),
			storedRoom.roomCode(),
			RoomStatus.valueOf(storedRoom.roomStatus()),
			storedRoom.createdAt()
		);
		List<WaitingRoomParticipant> participants = response.participants().stream()
			.map(stored -> stored.participant().toParticipant(stored.participantKey()))
			.toList();

		return JoinInviteRoomResult.joined(new WaitingRoomSnapshot(room, participants));
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

	record StoredRoom(
		String roomId,
		String roomType,
		String gameName,
		String roomCode,
		String roomStatus,
		java.time.Instant createdAt
	) {
	}

	record StoredParticipantEntry(
		String participantKey,
		StoredParticipant participant
	) {
	}
}
