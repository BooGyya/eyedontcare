package org.ssafy.b102.backend.guest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.guest.config.GuestSessionProperties;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.exception.GuestSessionErrorCode;
import org.ssafy.b102.backend.guest.repository.GuestSessionStore;
import org.ssafy.b102.backend.user.util.RandomNicknameGenerator;

@ExtendWith(MockitoExtension.class)
class GuestSessionServiceTest {

	private static final Duration SESSION_TTL = Duration.ofHours(24);
	private static final Instant NOW = Instant.parse("2026-07-30T12:00:00Z");
	private static final UUID FIRST_ID =
		UUID.fromString("27868019-1a91-40d3-8536-a0e5dcf7e8cf");
	private static final UUID SECOND_ID =
		UUID.fromString("20fa9eef-9d8f-47f7-b159-c9768a82d57d");

	@Mock
	private GuestSessionStore guestSessionStore;

	@Mock
	private RandomNicknameGenerator randomNicknameGenerator;

	private Queue<UUID> guestSessionIds;
	private GuestSessionService guestSessionService;

	@BeforeEach
	void setUp() {
		guestSessionIds = new ArrayDeque<>();
		guestSessionIds.add(FIRST_ID);
		guestSessionIds.add(SECOND_ID);
		guestSessionIds.add(UUID.fromString("092ae347-7051-49a7-8a7d-0575717d6c87"));

		Supplier<UUID> idSupplier = guestSessionIds::remove;
		guestSessionService = new GuestSessionService(
			guestSessionStore,
			randomNicknameGenerator,
			new GuestSessionProperties(SESSION_TTL),
			Clock.fixed(NOW, ZoneOffset.UTC),
			idSupplier
		);
	}

	@Test
	void issuesGuestSessionWithNicknameAndFixedExpiration() {
		when(randomNicknameGenerator.generate()).thenReturn("용감한수달0123");
		when(guestSessionStore.saveIfAbsent(
			org.mockito.ArgumentMatchers.eq(FIRST_ID),
			org.mockito.ArgumentMatchers.any(GuestSession.class),
			org.mockito.ArgumentMatchers.eq(SESSION_TTL)
		)).thenReturn(true);

		GuestSession issued = guestSessionService.issue();

		assertThat(issued.guestSessionId()).isEqualTo(FIRST_ID);
		assertThat(issued.nickname()).isEqualTo("용감한수달0123");
		assertThat(issued.createdAt()).isEqualTo(NOW);
		assertThat(issued.expiresAt()).isEqualTo(NOW.plus(SESSION_TTL));
		verify(randomNicknameGenerator).generate();
	}

	@Test
	void allowsSameNicknameForDifferentSessions() {
		when(randomNicknameGenerator.generate()).thenReturn("졸린판다0001");
		when(guestSessionStore.saveIfAbsent(
			org.mockito.ArgumentMatchers.any(UUID.class),
			org.mockito.ArgumentMatchers.any(GuestSession.class),
			org.mockito.ArgumentMatchers.eq(SESSION_TTL)
		)).thenReturn(true);

		GuestSession first = guestSessionService.issue();
		GuestSession second = guestSessionService.issue();

		assertThat(first.nickname()).isEqualTo(second.nickname());
		assertThat(first.guestSessionId()).isNotEqualTo(second.guestSessionId());
	}

	@Test
	void retriesWithNewUuidWhenUuidCollides() {
		when(randomNicknameGenerator.generate()).thenReturn("신나는토끼0002");
		when(guestSessionStore.saveIfAbsent(
			org.mockito.ArgumentMatchers.any(UUID.class),
			org.mockito.ArgumentMatchers.any(GuestSession.class),
			org.mockito.ArgumentMatchers.eq(SESSION_TTL)
		)).thenReturn(false, true);

		GuestSession issued = guestSessionService.issue();

		assertThat(issued.guestSessionId()).isEqualTo(SECOND_ID);
		ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
		verify(guestSessionStore, times(2)).saveIfAbsent(
			idCaptor.capture(),
			org.mockito.ArgumentMatchers.any(GuestSession.class),
			org.mockito.ArgumentMatchers.eq(SESSION_TTL)
		);
		assertThat(idCaptor.getAllValues()).containsExactly(FIRST_ID, SECOND_ID);
	}

	@Test
	void failsWhenUuidCollisionLimitIsExceeded() {
		when(randomNicknameGenerator.generate()).thenReturn("차분한여우0003");
		when(guestSessionStore.saveIfAbsent(
			org.mockito.ArgumentMatchers.any(UUID.class),
			org.mockito.ArgumentMatchers.any(GuestSession.class),
			org.mockito.ArgumentMatchers.eq(SESSION_TTL)
		)).thenReturn(false);

		assertThatThrownBy(guestSessionService::issue)
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GuestSessionErrorCode.GUEST_SESSION_STORE_UNAVAILABLE));

		verify(guestSessionStore, times(3)).saveIfAbsent(
			org.mockito.ArgumentMatchers.any(UUID.class),
			org.mockito.ArgumentMatchers.any(GuestSession.class),
			org.mockito.ArgumentMatchers.eq(SESSION_TTL)
		);
	}

	@Test
	void delegatesLookupValidationExistenceAndRemainingTtl() {
		GuestSession stored =
			new GuestSession(FIRST_ID, "명랑한고양이0004", NOW, NOW.plus(SESSION_TTL));
		when(guestSessionStore.findById(FIRST_ID))
			.thenReturn(Optional.of(stored), Optional.of(stored), Optional.of(stored));
		when(guestSessionStore.getRemainingTtl(FIRST_ID))
			.thenReturn(Optional.of(Duration.ofHours(23)));

		assertThat(guestSessionService.findById(FIRST_ID)).contains(stored);
		assertThat(guestSessionService.validate(FIRST_ID)).isEqualTo(stored);
		assertThat(guestSessionService.exists(FIRST_ID)).isTrue();
		assertThat(guestSessionService.getRemainingTtl(FIRST_ID))
			.contains(Duration.ofHours(23));
	}

	@Test
	void rejectsMissingGuestSession() {
		when(guestSessionStore.findById(FIRST_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> guestSessionService.validate(FIRST_ID))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GuestSessionErrorCode.INVALID_GUEST_SESSION));
	}
}
