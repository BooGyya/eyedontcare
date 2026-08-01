package org.ssafy.b102.backend.waitingroom.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.exception.GuestSessionErrorCode;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.exception.UserErrorCode;
import org.ssafy.b102.backend.user.repository.UserRepository;

class WaitingRoomParticipantResolverTest {

	private UserRepository userRepository;
	private GuestSessionService guestSessionService;
	private WaitingRoomParticipantResolver resolver;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		guestSessionService = mock(GuestSessionService.class);
		resolver = new WaitingRoomParticipantResolver(userRepository, guestSessionService);
	}

	@Test
	void memberTakesPriorityAndGuestHeaderIsIgnored() {
		User user = mock(User.class);
		UUID guestSessionId = UUID.randomUUID();
		when(user.getId()).thenReturn(7L);
		when(user.getNickname()).thenReturn("회원닉네임");
		when(userRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));

		ResolvedWaitingRoomParticipant result =
			resolver.resolve(new AuthenticatedUser(7L), guestSessionId);

		assertThat(result.participantKey()).isEqualTo("USER:7");
		assertThat(result.displayName()).isEqualTo("회원닉네임");
		assertThat(result.guestSessionId()).isNull();
		verify(guestSessionService, never()).findById(guestSessionId);
		verify(guestSessionService, never()).issue();
	}

	@Test
	void missingActiveMemberFailsWithUserNotFound() {
		when(userRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> resolver.resolve(new AuthenticatedUser(7L), null))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_NOT_FOUND));
		verify(guestSessionService, never()).issue();
	}

	@Test
	void validGuestSessionIsReused() {
		UUID guestSessionId = UUID.randomUUID();
		GuestSession session = guestSession(guestSessionId);
		when(guestSessionService.findById(guestSessionId)).thenReturn(Optional.of(session));

		ResolvedWaitingRoomParticipant result = resolver.resolve(null, guestSessionId);

		assertThat(result.participantKey()).isEqualTo("GUEST:" + guestSessionId);
		assertThat(result.guestSessionId()).isEqualTo(guestSessionId);
		assertThat(result.guestNickname()).isEqualTo("게스트닉네임");
		verify(guestSessionService, never()).issue();
	}

	@Test
	void missingGuestSessionIsIssued() {
		UUID requestedId = UUID.randomUUID();
		UUID issuedId = UUID.randomUUID();
		when(guestSessionService.findById(requestedId)).thenReturn(Optional.empty());
		when(guestSessionService.issue()).thenReturn(guestSession(issuedId));

		ResolvedWaitingRoomParticipant result = resolver.resolve(null, requestedId);

		assertThat(result.participantKey()).isEqualTo("GUEST:" + issuedId);
		assertThat(result.guestSessionId()).isEqualTo(issuedId);
	}

	@Test
	void existingMemberTakesPriorityWithoutGuestCalls() {
		User user = mock(User.class);
		UUID guestSessionId = UUID.randomUUID();
		when(user.getId()).thenReturn(7L);
		when(user.getNickname()).thenReturn("회원닉네임");
		when(userRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(user));

		ResolvedWaitingRoomParticipant result =
			resolver.resolveExisting(new AuthenticatedUser(7L), guestSessionId);

		assertThat(result.participantKey()).isEqualTo("USER:7");
		verify(guestSessionService, never()).validate(guestSessionId);
		verify(guestSessionService, never()).issue();
	}

	@Test
	void existingGuestIsValidatedWithoutIssuingSession() {
		UUID guestSessionId = UUID.randomUUID();
		when(guestSessionService.validate(guestSessionId))
			.thenReturn(guestSession(guestSessionId));

		ResolvedWaitingRoomParticipant result =
			resolver.resolveExisting(null, guestSessionId);

		assertThat(result.participantKey()).isEqualTo("GUEST:" + guestSessionId);
		verify(guestSessionService).validate(guestSessionId);
		verify(guestSessionService, never()).issue();
	}

	@Test
	void invalidExistingGuestDoesNotIssueSession() {
		when(guestSessionService.validate(null))
			.thenThrow(new BusinessException(GuestSessionErrorCode.INVALID_GUEST_SESSION));

		assertThatThrownBy(() -> resolver.resolveExisting(null, null))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(GuestSessionErrorCode.INVALID_GUEST_SESSION));
		verify(guestSessionService, never()).issue();
	}

	private GuestSession guestSession(UUID id) {
		Instant createdAt = Instant.parse("2026-07-30T04:00:00Z");
		return new GuestSession(id, "게스트닉네임", createdAt, createdAt.plusSeconds(3600));
	}
}
