package org.ssafy.b102.backend.matchmaking.repository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
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
 * <p>Lua 스크립트나 분산 락을 쓰지 않는다. 명령 순서만으로 경쟁 조건이 해소된다.
 * {@code ZADD NX}는 기존 score를 갱신하지 않고, {@code HSETNX}는 원자적 생성 판정을 제공하며,
 * {@code ZPOPMIN key 2}는 단일 원자 명령이다.
 */
@Repository
public class MatchmakingEntryRepository {

	private static final String DOMAIN = "matchmaking";
	private static final String QUEUE_RESOURCE = "queue";
	private static final String ENTRY_RESOURCE = "entry";

	private static final String FIELD_PARTICIPANT_KEY = "participantKey";
	private static final String FIELD_GAME_TYPE = "gameType";
	private static final String FIELD_MATCH_STATUS = "matchStatus";
	private static final String FIELD_WAITING_ROOM_ID = "waitingRoomId";
	private static final String FIELD_QUEUED_AT = "queuedAt";
	private static final String FIELD_STATUS_CHANGED_AT = "statusChangedAt";

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
	 * <p>{@code ZADD NX}를 먼저 실행하는 이유는 재신청이 최초 신청 시각을 덮어쓰지 못하게 하기 위해서다.
	 * 뒤이은 {@code HSETNX}가 실패하면 이미 신청한 참가자이고, 이때 앞의 {@code ZADD NX}도
	 * 아무것도 바꾸지 않았으므로 되돌릴 것이 없다.
	 *
	 * @return 새로 등록되면 {@code true}, 이미 신청한 참가자면 {@code false}
	 */
	public boolean enqueue(MatchmakingEntry entry) {
		String entryKey = entryKey(entry.participantKey());

		redisTemplate.opsForZSet()
			.addIfAbsent(queueKey(entry.gameType()), entry.participantKey(), entry.queueScore());

		Boolean created = redisTemplate.<String, String>opsForHash()
			.putIfAbsent(entryKey, FIELD_PARTICIPANT_KEY, entry.participantKey());
		if (!Boolean.TRUE.equals(created)) {
			return false;
		}

		save(entry);

		return true;
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
	 * <p>entry가 TTL로 사라져도 Sorted Set member는 만료되지 않는다. 그렇게 남은 유령 member는
	 * 꺼낸 뒤 되돌리지 않고 버려서 큐를 정리한다.
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

	private Map<String, String> toFields(MatchmakingEntry entry) {
		Map<String, String> fields = new LinkedHashMap<>();
		fields.put(FIELD_PARTICIPANT_KEY, entry.participantKey());
		fields.put(FIELD_GAME_TYPE, entry.gameType().name());
		fields.put(FIELD_MATCH_STATUS, entry.matchStatus().name());
		fields.put(FIELD_WAITING_ROOM_ID, toStoredValue(entry.waitingRoomId()));
		fields.put(FIELD_QUEUED_AT, String.valueOf(entry.queuedAt().toEpochMilli()));
		fields.put(FIELD_STATUS_CHANGED_AT, String.valueOf(entry.statusChangedAt().toEpochMilli()));

		return fields;
	}

	private MatchmakingEntry toEntry(Map<String, String> fields) {
		return new MatchmakingEntry(
			fields.get(FIELD_PARTICIPANT_KEY),
			GameName.valueOf(fields.get(FIELD_GAME_TYPE)),
			MatchStatus.valueOf(fields.get(FIELD_MATCH_STATUS)),
			toRoomId(fields.get(FIELD_WAITING_ROOM_ID)),
			toInstant(fields.get(FIELD_QUEUED_AT)),
			toInstant(fields.get(FIELD_STATUS_CHANGED_AT))
		);
	}

	private static String toStoredValue(UUID waitingRoomId) {
		return waitingRoomId == null ? ABSENT : waitingRoomId.toString();
	}

	private static UUID toRoomId(String stored) {
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
}
