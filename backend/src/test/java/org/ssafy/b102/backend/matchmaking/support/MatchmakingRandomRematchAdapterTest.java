package org.ssafy.b102.backend.matchmaking.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.matchmaking.exception.MatchmakingErrorCode;
import org.ssafy.b102.backend.matchmaking.service.MatchNotifier;
import org.ssafy.b102.backend.matchmaking.service.RandomRematchService;
import org.ssafy.b102.backend.waitingroom.service.RandomRematchRequestResult;

@ExtendWith(MockitoExtension.class)
class MatchmakingRandomRematchAdapterTest {

	private static final UUID ROOM_ID = UUID.fromString("f7f9e326-b12e-4fc9-92cb-927eb890de2a");
	private static final String PARTICIPANT_KEY = "USER:7";

	@Mock
	private RandomRematchService service;

	@Mock
	private MatchNotifier notifier;

	@InjectMocks
	private MatchmakingRandomRematchAdapter adapter;

	@Test
	void notifiesRequeuedOnlyOnceForNewRegistration() {
		when(service.requeueRemaining(ROOM_ID, GameName.HOCKEY, PARTICIPANT_KEY))
			.thenReturn(
				RandomRematchRequestResult.REQUEUED,
				RandomRematchRequestResult.ALREADY_REQUEUED
			);

		assertThat(adapter.requeueRemaining(ROOM_ID, GameName.HOCKEY, PARTICIPANT_KEY))
			.isEqualTo(RandomRematchRequestResult.REQUEUED);
		assertThat(adapter.requeueRemaining(ROOM_ID, GameName.HOCKEY, PARTICIPANT_KEY))
			.isEqualTo(RandomRematchRequestResult.ALREADY_REQUEUED);

		verify(notifier).notifyRequeued(PARTICIPANT_KEY, GameName.HOCKEY);
	}

	@Test
	void sendsMatchErrorForInvalidParticipant() {
		when(service.requeueRemaining(ROOM_ID, GameName.HOCKEY, PARTICIPANT_KEY))
			.thenReturn(RandomRematchRequestResult.PARTICIPANT_INVALID);

		assertThat(adapter.requeueRemaining(ROOM_ID, GameName.HOCKEY, PARTICIPANT_KEY))
			.isEqualTo(RandomRematchRequestResult.PARTICIPANT_INVALID);
		verify(notifier).notifyError(
			PARTICIPANT_KEY,
			MatchmakingErrorCode.REMATCH_PARTICIPANT_INVALID
		);
	}

	@Test
	void notificationFailureDoesNotRollbackRegistration() {
		when(service.requeueRemaining(ROOM_ID, GameName.HOCKEY, PARTICIPANT_KEY))
			.thenReturn(RandomRematchRequestResult.REQUEUED);
		doThrow(new IllegalStateException("closed"))
			.when(notifier).notifyRequeued(PARTICIPANT_KEY, GameName.HOCKEY);

		assertThat(adapter.requeueRemaining(ROOM_ID, GameName.HOCKEY, PARTICIPANT_KEY))
			.isEqualTo(RandomRematchRequestResult.REQUEUED);
		verify(notifier, never()).notifyError(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void redisFailureReturnsFailedAndSendsMatchError() {
		when(service.requeueRemaining(ROOM_ID, GameName.HOCKEY, PARTICIPANT_KEY))
			.thenThrow(new IllegalStateException("redis unavailable"));

		assertThat(adapter.requeueRemaining(ROOM_ID, GameName.HOCKEY, PARTICIPANT_KEY))
			.isEqualTo(RandomRematchRequestResult.FAILED);
		verify(notifier).notifyError(PARTICIPANT_KEY, MatchmakingErrorCode.REMATCH_FAILED);
	}
}
