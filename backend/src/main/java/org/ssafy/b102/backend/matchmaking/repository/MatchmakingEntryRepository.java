package org.ssafy.b102.backend.matchmaking.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Repository;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.common.redis.RedisKeyBuilder;
import org.ssafy.b102.backend.matchmaking.entity.MatchStatus;
import org.ssafy.b102.backend.matchmaking.entity.MatchmakingEntry;

/**
 * 매칭 큐와 참가자 상태를 Redis에 저장한다.
 *
 * <ul>
 *   <li>매칭 큐: Sorted Set. member는 참가자 키, score는 {@code queuedAt}의 epoch milli</li>
 *   <li>참가자 상태: Hash. ERD의 {@code matchmaking_entries} 필드와 1:1로 대응</li>
 * </ul>
 *
 * <p>Hash와 Sorted Set을 함께 변경해야 하는 연산은 Redis Lua 스크립트로 원자 처리한다.
 * 후보 선점은 {@code ZPOPMIN}으로 원자 처리하고, 상태 전이는 기존 값 비교를 통해 stale
 * callback이 새 매칭을 건드리지 않도록 한다.
 */
@Repository
public class MatchmakingEntryRepository {

	private static final String DOMAIN = "matchmaking";
	private static final String QUEUE_RESOURCE = "queue";
	private static final String ENTRY_RESOURCE = "entry";
	private static final String REMATCH_RESOURCE = "rematch";
	private static final DefaultRedisScript<Long> REQUEUE_REMAINING_SCRIPT =
		longScript("redis/matchmaking/requeue-remaining.lua");
	private static final DefaultRedisScript<Long> ENQUEUE_SCRIPT =
		longScript("redis/matchmaking/enqueue.lua");
	private static final DefaultRedisScript<Long> DELETE_BY_ROOM_SCRIPT =
		longScript("redis/matchmaking/delete-by-room.lua");
	private static final DefaultRedisScript<Long> CLEANUP_STALE_SEARCHING_SCRIPT =
		longScript("redis/matchmaking/cleanup-stale-searching.lua");

	private static final String FIELD_PARTICIPANT_KEY = "participantKey";
	private static final String FIELD_GAME_TYPE = "gameType";
	private static final String FIELD_MATCH_STATUS = "matchStatus";
	private static final String FIELD_WAITING_ROOM_ID = "waitingRoomId";
	private static final String FIELD_QUEUED_AT = "queuedAt";
	private static final String FIELD_STATUS_CHANGED_AT = "statusChangedAt";
	private static final String FIELD_MATCH_ATTEMPT_ID = "matchAttemptId";

	private static final Set<String> REQUIRED_FIELDS = Set.of(
		FIELD_PARTICIPANT_KEY,
		FIELD_GAME_TYPE,
		FIELD_MATCH_STATUS,
		FIELD_QUEUED_AT,
		FIELD_STATUS_CHANGED_AT
	);

	/**
	 * Hash에는 null을 저장할 수 없어 빈 문자열을 부재 표시로 쓴다.
	 */
	private static final String ABSENT = "";

	/**
	 * 유령 member가 쌓여 있어도 무한 루프에 빠지지 않도록 재시도를 제한한다.
	 */
	private static final int MAX_POP_ATTEMPTS = 5;

	private final StringRedisTemplate redisTemplate;
	private final RedisKeyBuilder redisKeyBuilder;
	private final Duration entryTtl;

	public MatchmakingEntryRepository(
		StringRedisTemplate redisTemplate,
		RedisKeyBuilder redisKeyBuilder,
		@Value("${app.matchmaking.entry-ttl:PT30M}") Duration entryTtl
	) {
		this.redisTemplate = redisTemplate;
		this.redisKeyBuilder = redisKeyBuilder;
		this.entryTtl = entryTtl;
	}

	/**
	 * 큐에 등록하고 상태를 기록한다.
	 *
	 * <p>기존 entry가 있으면 아무것도 추가하지 않는다. entry가 없고 ZSET member만 남아 있으면
	 * 해당 ghost member를 먼저 제거한 뒤 새 entry와 member를 함께 생성한다. 두 작업은 하나의
	 * Redis script 안에서 실행되므로 명령 사이에 다른 요청이 끼어들 수 없다.
	 *
	 * @return 새로 등록되면 {@code true}, 이미 entry가 있으면 {@code false}
	 */
	public boolean enqueue(MatchmakingEntry entry) {
		List<String> keys = new ArrayList<>();
		keys.add(entryKey(entry.participantKey()));
		keys.add(queueKey(entry.gameType()));
		Arrays.stream(GameName.values())
			.filter(game -> game != entry.gameType())
			.map(this::queueKey)
			.forEach(keys::add);
		Long result = redisTemplate.execute(
			ENQUEUE_SCRIPT,
			keys,
			entry.participantKey(),
			entry.gameType().name(),
			entry.matchStatus().name(),
			toStoredValue(entry.waitingRoomId()),
			String.valueOf(entry.queuedAt().toEpochMilli()),
			String.valueOf(entry.statusChangedAt().toEpochMilli()),
			toStoredValue(entry.matchAttemptId()),
			String.valueOf(entryTtl.toMillis())
		);
		return Long.valueOf(1L).equals(result);
	}

	public void save(MatchmakingEntry entry) {
		String entryKey = entryKey(entry.participantKey());

		redisTemplate.<String, String>opsForHash().putAll(entryKey, toFields(entry));
		redisTemplate.expire(entryKey, entryTtl);
	}

	public Optional<MatchmakingEntry> find(String participantKey) {
		Map<String, String> fields = redisTemplate.<String, String>opsForHash()
			.entries(entryKey(participantKey));

		if (fields == null || !fields.keySet().containsAll(REQUIRED_FIELDS)) {
			return Optional.empty();
		}

		return Optional.of(toEntry(fields));
	}

	public void delete(String participantKey) {
		find(participantKey).ifPresent(entry ->
			redisTemplate.opsForZSet().remove(queueKey(entry.gameType()), participantKey));

		redisTemplate.delete(entryKey(participantKey));
	}

	/**
	 * 대기 순서가 빠른 참가자를 큐에서 꺼낸다(선점).
	 *
	 * <p>큐에 남아 있어도 후보가 아닌 member를 두 가지 걸러낸다. 둘 다 꺼낸 뒤 되돌리지 않고
	 * 버려서 큐를 정리한다.
	 *
	 * <ul>
	 *   <li>entry가 없는 member: TTL로 entry가 사라져도 Sorted Set member는 만료되지 않아 남는다.</li>
	 *   <li>{@code SEARCHING}이 아닌 member: 상태가 이미 성사된 참가자는 큐에서 제거되어야 하며,
	 *       남아 있더라도 후보 선점 시 버려서 두 번째 방이 만들어지지 않게 한다.</li>
	 * </ul>
	 *
	 * @return 요청한 수보다 적게 반환될 수 있다. 매칭이 불가능하면 호출자가 되돌려야 한다.
	 */
	public List<MatchmakingEntry> popCandidates(GameName gameType, int count) {
		String queueKey = queueKey(gameType);
		List<MatchmakingEntry> candidates = new ArrayList<>(count);

		for (int attempt = 0; attempt < MAX_POP_ATTEMPTS && candidates.size() < count; attempt++) {
			Set<TypedTuple<String>> popped = redisTemplate.opsForZSet()
				.popMin(queueKey, count - candidates.size());

			if (popped == null || popped.isEmpty()) {
				break;
			}

			popped.stream()
				.map(TypedTuple::getValue)
				.filter(Objects::nonNull)
				.map(this::find)
				.flatMap(Optional::stream)
				.filter(MatchmakingEntry::isSearching)
				.forEach(candidates::add);
		}

		return candidates;
	}

	/**
	 * 선점을 해제하고 원래 대기 순서로 되돌린다. {@code queuedAt}이 그대로이므로 순서가 밀리지 않는다.
	 */
	public void requeue(MatchmakingEntry entry) {
		redisTemplate.opsForZSet()
			.add(queueKey(entry.gameType()), entry.participantKey(), entry.queueScore());
	}

	/**
	 * 대기 순서가 빠른 참가자 {@code count}명을 하나의 {@code matchAttemptId}로 예약한다({@code MATCHING}).
	 *
	 * <p>{@code count}명을 채우지 못하면 꺼낸 참가자를 원래 순서로 되돌리고 빈 목록을 반환한다.
	 * 채우면 각 entry를 {@code MATCHING}으로 저장하고 예약된 목록을 반환한다. 예약된 참가자는
	 * 큐에서 빠져 있어 다른 매칭 시도가 다시 선점할 수 없다.
	 *
	 * @return 정확히 {@code count}명을 예약하면 그 목록, 아니면 빈 목록
	 */
	public List<MatchmakingEntry> reserveCandidates(GameName gameType, UUID matchAttemptId, int count) {
		List<MatchmakingEntry> candidates = popCandidates(gameType, count);
		if (candidates.size() < count) {
			candidates.forEach(this::requeue);
			return List.of();
		}

		Instant now = Instant.now();
		List<MatchmakingEntry> reserved = candidates.stream()
			.map(candidate -> candidate.reserve(matchAttemptId, now))
			.toList();
		reserved.forEach(this::save);

		return reserved;
	}

	/**
	 * 예약된 참가자를 {@code ENTERING_ROOM}으로 확정한다.
	 *
	 * <p>먼저 모든 참가자가 여전히 같은 {@code matchAttemptId}로 예약돼 있는지 재확인한다.
	 * 한 명이라도 취소·변경됐으면 아무것도 바꾸지 않고 {@code false}를 반환한다(부분 확정 방지).
	 *
	 * @return 모두 확정하면 {@code true}, 아니면 {@code false}
	 */
	public boolean finalizeToRoom(
		List<MatchmakingEntry> reserved,
		UUID matchAttemptId,
		UUID waitingRoomId
	) {
		List<MatchmakingEntry> current = new ArrayList<>(reserved.size());
		for (MatchmakingEntry entry : reserved) {
			Optional<MatchmakingEntry> found = find(entry.participantKey());
			if (found.isEmpty() || !found.get().isReservedBy(matchAttemptId)) {
				return false;
			}
			current.add(found.get());
		}

		Instant now = Instant.now();
		current.forEach(entry -> save(entry.enterRoom(waitingRoomId, now)));

		return true;
	}

	/**
	 * 방 생성 실패 등의 보상. 예약된 참가자를 현재 시각 기준으로 다시 {@code SEARCHING} 등록한다.
	 *
	 * <p>기존 대기 순서를 승계하지 않는다({@code queuedAt}·score 모두 현재 시각). stale 콜백이
	 * 새 매칭 entry를 건드리지 못하도록, 여전히 같은 {@code matchAttemptId}로 예약된 경우에만 되돌린다.
	 * 참가자(USER·GUEST) 유효성 재검증은 서비스 계층 책임이다.
	 *
	 * @return 되돌렸으면 {@code true}, stale이라 건드리지 않았으면 {@code false}
	 */
	public boolean reregisterAtCurrentTime(MatchmakingEntry reserved, UUID matchAttemptId) {
		Optional<MatchmakingEntry> found = find(reserved.participantKey());
		if (found.isEmpty() || !found.get().isReservedBy(matchAttemptId)) {
			return false;
		}

		MatchmakingEntry current = found.get();
		MatchmakingEntry requeued =
			MatchmakingEntry.searching(current.participantKey(), current.gameType(), Instant.now());
		redisTemplate.opsForZSet()
			.add(queueKey(requeued.gameType()), requeued.participantKey(), requeued.queueScore());
		save(requeued);

		return true;
	}

	public RematchRegistrationResult requeueRemaining(
		UUID previousRoomId,
		GameName gameType,
		String participantKey,
		Instant now
	) {
		long epochMilli = now.toEpochMilli();
		Long result = redisTemplate.execute(
			REQUEUE_REMAINING_SCRIPT,
			List.of(
				entryKey(participantKey),
				queueKey(gameType),
				rematchKey(participantKey, previousRoomId)
			),
			previousRoomId.toString(),
			gameType.name(),
			String.valueOf(epochMilli),
			String.valueOf(entryTtl.toMillis()),
			participantKey
		);

		if (Long.valueOf(1L).equals(result)) {
			return RematchRegistrationResult.REQUEUED;
		}
		if (Long.valueOf(2L).equals(result)) {
			return RematchRegistrationResult.ALREADY_REQUEUED;
		}
		return RematchRegistrationResult.STALE;
	}

	/**
	 * 대기방 입장 확인. {@code ENTERING_ROOM} 참가자를 {@code IN_WAITING_ROOM}으로 전환한다.
	 *
	 * <p>{@code roomId}가 일치할 때만 전환한다. 이미 {@code IN_WAITING_ROOM}이면 멱등 성공,
	 * 다른 {@code roomId}이거나 다른 상태면 건드리지 않는다(stale 콜백 보호).
	 *
	 * @return 전환했거나 이미 입장 상태면 {@code true}, 대상이 아니면 {@code false}
	 */
	public boolean markEntered(String participantKey, UUID roomId) {
		Optional<MatchmakingEntry> found = find(participantKey);
		if (found.isEmpty()) {
			return false;
		}

		MatchmakingEntry entry = found.get();
		if (!roomId.equals(entry.waitingRoomId())) {
			return false;
		}
		if (entry.matchStatus() == MatchStatus.IN_WAITING_ROOM) {
			return true;
		}
		if (entry.matchStatus() == MatchStatus.ENTERING_ROOM) {
			save(entry.enterWaitingRoom(Instant.now()));
			return true;
		}

		return false;
	}

	/**
	 * 대기방 IN_GAME 완료 후 entry를 삭제한다(compare-delete).
	 *
	 * <p>반드시 {@code roomId}를 비교한다. participantKey만 비교해 삭제하면 과거 콜백이 새 매칭
	 * entry를 지울 수 있다. 이미 삭제됐으면 멱등 성공, 다른 {@code roomId}면 건드리지 않는다.
	 *
	 * @return 삭제했거나 이미 없으면 {@code true}, 다른 {@code roomId}라 보호했으면 {@code false}
	 */
	public boolean completeAndDelete(String participantKey, UUID roomId) {
		return deleteIfRoomMatches(participantKey, roomId) != EntryDeleteResult.ROOM_MISMATCH;
	}

	/**
	 * participantKey와 roomId가 모두 현재 entry와 일치할 때만 entry와 모든 게임 큐 member를
	 * 함께 삭제한다.
	 */
	public EntryDeleteResult deleteIfRoomMatches(String participantKey, UUID roomId) {
		return deleteIfRoomMatches(participantKey, roomId, false);
	}

	/**
	 * WebSocket 인증 실패 보상용 compare-delete. 방 입장 단계의 상태만 대상으로 하여 stale
	 * callback이 SEARCHING entry를 삭제하지 않도록 한다.
	 */
	public EntryDeleteResult deleteEnteringRoomIfMatches(String participantKey, UUID roomId) {
		return deleteIfRoomMatches(participantKey, roomId, true);
	}

	/**
	 * SEARCHING Hash는 남았지만 해당 게임 ZSET member가 없는 경우에만 entry를 정리한다.
	 * 상태 확인과 삭제를 하나의 script에서 수행해, 정리 중 새 정상 entry가 만들어지면 보호한다.
	 */
	public boolean deleteStaleSearchingIfQueueMissing(String participantKey, GameName gameType) {
		Long result = redisTemplate.execute(
			CLEANUP_STALE_SEARCHING_SCRIPT,
			List.of(entryKey(participantKey), queueKey(gameType)),
			participantKey,
			gameType.name()
		);
		return Long.valueOf(1L).equals(result);
	}

	private EntryDeleteResult deleteIfRoomMatches(
		String participantKey,
		UUID roomId,
		boolean enteringRoomOnly
	) {
		List<String> keys = new ArrayList<>();
		keys.add(entryKey(participantKey));
		keys.addAll(allQueueKeys());
		Long result = redisTemplate.execute(
			DELETE_BY_ROOM_SCRIPT,
			keys,
			roomId.toString(),
			participantKey,
			Boolean.toString(enteringRoomOnly)
		);
		if (Long.valueOf(1L).equals(result)) {
			return EntryDeleteResult.DELETED;
		}
		if (Long.valueOf(0L).equals(result)) {
			return EntryDeleteResult.NOT_FOUND;
		}
		return EntryDeleteResult.ROOM_MISMATCH;
	}

	private List<String> allQueueKeys() {
		return Arrays.stream(GameName.values()).map(this::queueKey).toList();
	}

	private Map<String, String> toFields(MatchmakingEntry entry) {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put(FIELD_PARTICIPANT_KEY, entry.participantKey());
		fields.put(FIELD_GAME_TYPE, entry.gameType().name());
		fields.put(FIELD_MATCH_STATUS, entry.matchStatus().name());
		fields.put(FIELD_WAITING_ROOM_ID, toStoredValue(entry.waitingRoomId()));
		fields.put(FIELD_QUEUED_AT, String.valueOf(entry.queuedAt().toEpochMilli()));
		fields.put(FIELD_STATUS_CHANGED_AT, String.valueOf(entry.statusChangedAt().toEpochMilli()));
		fields.put(FIELD_MATCH_ATTEMPT_ID, toStoredValue(entry.matchAttemptId()));

		return fields;
	}

	private MatchmakingEntry toEntry(Map<String, String> fields) {
		return new MatchmakingEntry(
			fields.get(FIELD_PARTICIPANT_KEY),
			GameName.valueOf(fields.get(FIELD_GAME_TYPE)),
			MatchStatus.valueOf(fields.get(FIELD_MATCH_STATUS)),
			toUuid(fields.get(FIELD_WAITING_ROOM_ID)),
			toInstant(fields.get(FIELD_QUEUED_AT)),
			toInstant(fields.get(FIELD_STATUS_CHANGED_AT)),
			toUuid(fields.get(FIELD_MATCH_ATTEMPT_ID))
		);
	}

	private static String toStoredValue(UUID value) {
		return value == null ? ABSENT : value.toString();
	}

	private static UUID toUuid(String stored) {
		return stored == null || stored.isEmpty() ? null : UUID.fromString(stored);
	}

	private static Instant toInstant(String epochMilli) {
		return Instant.ofEpochMilli(Long.parseLong(epochMilli));
	}

	private String queueKey(GameName gameType) {
		return redisKeyBuilder.build(DOMAIN, QUEUE_RESOURCE, gameType.name());
	}

	private String entryKey(String participantKey) {
		return redisKeyBuilder.build(DOMAIN, ENTRY_RESOURCE, participantKey);
	}

	private String rematchKey(String participantKey, UUID previousRoomId) {
		return redisKeyBuilder.build(
			DOMAIN,
			REMATCH_RESOURCE,
			participantKey,
			previousRoomId.toString()
		);
	}

	private static DefaultRedisScript<Long> longScript(String path) {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>();
		script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
		script.setResultType(Long.class);
		return script;
	}

	public enum EntryDeleteResult {
		DELETED,
		NOT_FOUND,
		ROOM_MISMATCH
	}
}
