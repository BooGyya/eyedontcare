package org.ssafy.b102.backend.waitingroom.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.waitingroom.entity.RoomRole;
import org.ssafy.b102.testfixture.websocket.StubWebSocketSession;

class InMemoryWaitingRoomWebSocketSessionRegistryTest {

	private static final UUID ROOM_ID =
		UUID.fromString("c93c76b2-7f78-4275-b8af-7cdd921bbb4f");

	@Test
	void registersFindsSuppressesAndUnregistersContext() {
		InMemoryWaitingRoomWebSocketSessionRegistry registry =
			new InMemoryWaitingRoomWebSocketSessionRegistry();
		WaitingRoomConnectionContext context = context("s1", "USER:1");

		assertThat(registry.registerIfAbsent(context)).isTrue();
		assertThat(registry.findBySessionId("s1")).contains(context);
		assertThat(
			registry.findByRoomAndParticipant(ROOM_ID, "USER:1")
		).contains(context);
		assertThat(registry.findByRoomId(ROOM_ID)).containsExactly(context);

		registry.markSuppressLeave("s1");
		assertThat(context.isLeaveSuppressed()).isTrue();
		assertThat(registry.unregister("s1")).contains(context);
		assertThat(registry.findBySessionId("s1")).isEmpty();
	}

	@Test
	void concurrentDuplicateRegistrationAllowsExactlyOneSession()
		throws Exception {

		InMemoryWaitingRoomWebSocketSessionRegistry registry =
			new InMemoryWaitingRoomWebSocketSessionRegistry();
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		try {
			Future<Boolean> first = executor.submit(() -> {
				start.await();
				return registry.registerIfAbsent(context("s1", "USER:1"));
			});
			Future<Boolean> second = executor.submit(() -> {
				start.await();
				return registry.registerIfAbsent(context("s2", "USER:1"));
			});
			start.countDown();

			boolean firstResult = first.get();
			boolean secondResult = second.get();
			assertThat(firstResult).isNotEqualTo(secondResult);
			assertThat(registry.findByRoomId(ROOM_ID)).hasSize(1);
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void differentParticipantsCanRegisterInSameRoom() {
		InMemoryWaitingRoomWebSocketSessionRegistry registry =
			new InMemoryWaitingRoomWebSocketSessionRegistry();

		assertThat(registry.registerIfAbsent(context("s1", "USER:1")))
			.isTrue();
		assertThat(registry.registerIfAbsent(context("s2", "USER:2")))
			.isTrue();
		assertThat(registry.findByRoomId(ROOM_ID)).hasSize(2);
	}

	private WaitingRoomConnectionContext context(
		String sessionId,
		String participantKey
	) {
		return new WaitingRoomConnectionContext(
			sessionId,
			ROOM_ID,
			participantKey,
			RoomRole.HOST,
			Instant.parse("2026-07-30T04:00:00Z"),
			new StubWebSocketSession(sessionId)
		);
	}
}
