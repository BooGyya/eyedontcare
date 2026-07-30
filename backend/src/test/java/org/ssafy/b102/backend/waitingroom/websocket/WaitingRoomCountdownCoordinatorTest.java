package org.ssafy.b102.backend.waitingroom.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

class WaitingRoomCountdownCoordinatorTest {

	@Test
	void roomHasOnlyOneTaskAndCancelRemovesIt() {
		TaskScheduler scheduler = mock(TaskScheduler.class);
		ScheduledFuture<?> future = mock(ScheduledFuture.class);
		doReturn(future)
			.when(scheduler)
			.schedule(any(Runnable.class), any(Instant.class));
		WaitingRoomCountdownCoordinator coordinator =
			new WaitingRoomCountdownCoordinator(scheduler);
		UUID roomId = UUID.randomUUID();
		Instant endsAt = Instant.parse("2026-07-30T04:00:03Z");

		assertThat(
			coordinator.scheduleIfAbsent(roomId, endsAt, () -> {
			})
		).isTrue();
		assertThat(
			coordinator.scheduleIfAbsent(roomId, endsAt, () -> {
			})
		).isFalse();
		assertThat(coordinator.isScheduled(roomId)).isTrue();

		coordinator.cancel(roomId);

		assertThat(coordinator.isScheduled(roomId)).isFalse();
	}
}
