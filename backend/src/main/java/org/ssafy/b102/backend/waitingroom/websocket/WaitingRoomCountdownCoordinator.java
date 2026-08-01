package org.ssafy.b102.backend.waitingroom.websocket;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class WaitingRoomCountdownCoordinator {

	private final TaskScheduler taskScheduler;
	private final Map<UUID, ScheduledFuture<?>> tasks = new HashMap<>();

	public WaitingRoomCountdownCoordinator(
		@Qualifier("waitingRoomWebSocketTaskScheduler")
		TaskScheduler taskScheduler
	) {
		this.taskScheduler = taskScheduler;
	}

	public synchronized boolean scheduleIfAbsent(
		UUID roomId,
		Instant countdownEndsAt,
		Runnable action
	) {
		if (tasks.containsKey(roomId)) {
			return false;
		}
		ScheduledFuture<?> task = taskScheduler.schedule(
			() -> {
				try {
					action.run();
				} finally {
					remove(roomId);
				}
			},
			countdownEndsAt
		);
		if (task == null) {
			throw new IllegalStateException("Countdown task was not scheduled");
		}
		tasks.put(roomId, task);
		return true;
	}

	public synchronized boolean cancel(UUID roomId) {
		ScheduledFuture<?> task = tasks.remove(roomId);
		return task != null && task.cancel(false);
	}

	public synchronized boolean isScheduled(UUID roomId) {
		return tasks.containsKey(roomId);
	}

	private synchronized void remove(UUID roomId) {
		tasks.remove(roomId);
	}
}
