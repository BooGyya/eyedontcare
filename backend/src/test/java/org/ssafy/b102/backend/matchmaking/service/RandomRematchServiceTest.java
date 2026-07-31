package org.ssafy.b102.backend.matchmaking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.matchmaking.repository.MatchmakingEntryRepository;
import org.ssafy.b102.backend.matchmaking.repository.RematchRegistrationResult;
import org.ssafy.b102.backend.user.repository.UserRepository;
import org.ssafy.b102.backend.waitingroom.service.RandomRematchRequestResult;

@ExtendWith(MockitoExtension.class)
class RandomRematchServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-31T03:00:00Z");
	private static final UUID ROOM_ID = UUID.fromString("f7f9e326-b12e-4fc9-92cb-927eb890de2a");

	@Mock
	private MatchmakingEntryRepository repository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private GuestSessionService guestSessionService;

	private RandomRematchService service;

	@BeforeEach
	void setUp() {
		service = new RandomRematchService(
			repository,
			userRepository,
			guestSessionService,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void requeuesActiveUser() {
		when(userRepository.existsByIdAndDeletedAtIsNull(7L)).thenReturn(true);
		when(repository.requeueRemaining(ROOM_ID, GameName.HOCKEY, "USER:7", NOW))
			.thenReturn(RematchRegistrationResult.REQUEUED);

		RandomRematchRequestResult result =
			service.requeueRemaining(ROOM_ID, GameName.HOCKEY, "USER:7");

		assertThat(result).isEqualTo(RandomRematchRequestResult.REQUEUED);
	}

	@Test
	void requeuesExistingGuestWithoutIssuingNewSession() {
		UUID guestId = UUID.randomUUID();
		when(guestSessionService.exists(guestId)).thenReturn(true);
		when(repository.requeueRemaining(
			ROOM_ID,
			GameName.EYEFIGHT,
			"GUEST:" + guestId,
			NOW
		)).thenReturn(RematchRegistrationResult.REQUEUED);

		RandomRematchRequestResult result = service.requeueRemaining(
			ROOM_ID,
			GameName.EYEFIGHT,
			"GUEST:" + guestId
		);

		assertThat(result).isEqualTo(RandomRematchRequestResult.REQUEUED);
		verify(guestSessionService).exists(guestId);
	}

	@Test
	void rejectsInactiveUserWithoutWritingRedis() {
		when(userRepository.existsByIdAndDeletedAtIsNull(7L)).thenReturn(false);

		RandomRematchRequestResult result =
			service.requeueRemaining(ROOM_ID, GameName.HOCKEY, "USER:7");

		assertThat(result).isEqualTo(RandomRematchRequestResult.PARTICIPANT_INVALID);
		verify(repository, never()).requeueRemaining(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()
		);
	}

	@Test
	void rejectsMalformedParticipantKey() {
		RandomRematchRequestResult result =
			service.requeueRemaining(ROOM_ID, GameName.HOCKEY, "USER:not-a-number");

		assertThat(result).isEqualTo(RandomRematchRequestResult.PARTICIPANT_INVALID);
		verify(repository, never()).requeueRemaining(
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any(),
			org.mockito.ArgumentMatchers.any()
		);
	}
}
