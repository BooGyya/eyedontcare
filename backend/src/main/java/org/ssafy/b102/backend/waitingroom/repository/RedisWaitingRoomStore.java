package org.ssafy.b102.backend.waitingroom.repository;

import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Repository;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
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

	private List<String> keys(WaitingRoom room) {
		String roomId = room.roomId().toString();
		return List.of(
			redisKeyBuilder.build(DOMAIN, "room", roomId),
			redisKeyBuilder.build(DOMAIN, "participants", roomId),
			redisKeyBuilder.build(DOMAIN, "invite-code", room.roomCode())
		);
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

	private static BusinessException storeUnavailable() {
		return new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE);
	}

	record StoredParticipant(
		String displayName,
		String roomRole,
		int slotNo,
		boolean isReady,
		String calibrationStatus,
		java.time.Instant joinedAt
	) {

		static StoredParticipant from(WaitingRoomParticipant participant) {
			return new StoredParticipant(
				participant.displayName(),
				participant.roomRole().name(),
				participant.slotNo(),
				participant.isReady(),
				participant.calibrationStatus().name(),
				participant.joinedAt()
			);
		}
	}
}
