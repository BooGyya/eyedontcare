package org.ssafy.b102.backend.waitingroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ssafy.b102.backend.game.service.GameService;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.waitingroom.config.WaitingRoomProperties;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
import org.ssafy.b102.backend.waitingroom.repository.LeaveWaitingRoomCommand;
import org.ssafy.b102.backend.waitingroom.repository.LeaveWaitingRoomResult;
import org.ssafy.b102.backend.waitingroom.repository.LeaveRandomRoomCommand;
import org.ssafy.b102.backend.waitingroom.repository.RandomRoomLeaveResult;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomMetadata;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomStore;
import org.ssafy.b102.backend.waitingroom.support.InviteCodeGenerator;
import org.ssafy.b102.backend.waitingroom.support.ResolvedWaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.support.RoomIdGenerator;
import org.ssafy.b102.backend.waitingroom.support.WaitingRoomParticipantResolver;

class WaitingRoomLeaveServiceTest {

	private static final UUID ROOM_ID =
		UUID.fromString("c93c76b2-7f78-4275-b8af-7cdd921bbb4f");

	private WaitingRoomParticipantResolver resolver;
	private WaitingRoomStore store;
	private WaitingRoomService service;

	@BeforeEach
	void setUp() {
		resolver = mock(WaitingRoomParticipantResolver.class);
		store = mock(WaitingRoomStore.class);
		service = new WaitingRoomService(
			mock(GameService.class),
			resolver,
			store,
			new WaitingRoomProperties(
				Duration.ofMinutes(10),
				Duration.ofSeconds(30),
				2,
				20,
				Duration.ofSeconds(3)
			),
			mock(InviteCodeGenerator.class),
			mock(RoomIdGenerator.class)
		);
	}

	@Test
	void missingRoomFailsBeforeResolvingIdentity() {
		when(store.findRoomMetadata(ROOM_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.leave(ROOM_ID, null, null))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode())
					.isEqualTo(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND));
		verify(resolver, never()).resolveExisting(any(), any());
		verify(store, never()).leaveAtomically(any());
	}

	@Test
	void leaveResolvesExistingIdentityAndPassesConfiguredTtls() {
		AuthenticatedUser member = new AuthenticatedUser(1L);
		when(store.findRoomMetadata(ROOM_ID)).thenReturn(Optional.of(metadata()));
		when(resolver.resolveExisting(member, null))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "회원"));
		when(store.leaveAtomically(any())).thenReturn(LeaveWaitingRoomResult.LEFT);

		WaitingRoomLeaveOutcome outcome = service.leave(ROOM_ID, member, null);

		ArgumentCaptor<LeaveWaitingRoomCommand> captor =
			ArgumentCaptor.forClass(LeaveWaitingRoomCommand.class);
		verify(store).leaveAtomically(captor.capture());
		assertThat(captor.getValue().roomId()).isEqualTo(ROOM_ID);
		assertThat(captor.getValue().roomCode()).isEqualTo("0123");
		assertThat(captor.getValue().participantKey()).isEqualTo("USER:1");
		assertThat(captor.getValue().maxParticipants()).isEqualTo(2);
		assertThat(captor.getValue().activeTtl()).isEqualTo(Duration.ofMinutes(10));
		assertThat(captor.getValue().closedTtl()).isEqualTo(Duration.ofSeconds(30));
		assertThat(outcome.roomId()).isEqualTo(ROOM_ID);
		assertThat(outcome.participantKey()).isEqualTo("USER:1");
		assertThat(outcome.inviteResult()).isEqualTo(LeaveWaitingRoomResult.LEFT);
	}

	@Test
	void allSuccessfulLeaveResultsReturnNormally() {
		for (LeaveWaitingRoomResult result : new LeaveWaitingRoomResult[]{
			LeaveWaitingRoomResult.LEFT,
			LeaveWaitingRoomResult.ROOM_CLOSED,
			LeaveWaitingRoomResult.ALREADY_CLOSED
		}) {
			when(store.findRoomMetadata(ROOM_ID)).thenReturn(Optional.of(metadata()));
			when(store.leaveAtomically(any())).thenReturn(result);

			service.leaveByParticipantKey(ROOM_ID, "USER:1");
		}
	}

	@Test
	void randomLeaveUsesDedicatedStoreAndReturnsStructuredOutcome() {
		when(store.findRoomMetadata(ROOM_ID))
			.thenReturn(
				Optional.of(
					new WaitingRoomMetadata(
						ROOM_ID,
						RoomType.RANDOM,
						RoomStatus.WAITING,
						null
					)
				)
			);
		RandomRoomLeaveResult result = new RandomRoomLeaveResult(
			RandomRoomLeaveResult.Status.CLOSED_NOW,
			ROOM_ID,
			GameName.EYEFIGHT,
			"USER:1",
			"USER:2",
			RoomStatus.WAITING
		);
		when(store.leaveRandomRoomAtomically(any())).thenReturn(result);

		WaitingRoomLeaveOutcome outcome =
			service.leaveWithOutcomeByParticipantKey(ROOM_ID, "USER:1");

		assertThat(outcome.roomType()).isEqualTo(RoomType.RANDOM);
		assertThat(outcome.randomResult()).isEqualTo(result);
		ArgumentCaptor<LeaveRandomRoomCommand> captor =
			ArgumentCaptor.forClass(LeaveRandomRoomCommand.class);
		verify(store).leaveRandomRoomAtomically(captor.capture());
		assertThat(captor.getValue().participantKey()).isEqualTo("USER:1");
		assertThat(captor.getValue().closedTtl())
			.isEqualTo(Duration.ofSeconds(30));
		verify(store, never()).leaveAtomically(any());
	}

	@Test
	void mapsStoreLeaveResultsToDomainErrors() {
		assertResultError(
			LeaveWaitingRoomResult.ROOM_NOT_FOUND,
			WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND
		);
		assertResultError(
			LeaveWaitingRoomResult.PARTICIPANT_NOT_FOUND,
			WaitingRoomErrorCode.PARTICIPANT_NOT_FOUND
		);
		assertResultError(
			LeaveWaitingRoomResult.CORRUPTED,
			WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE
		);
	}

	private void assertResultError(
		LeaveWaitingRoomResult result,
		WaitingRoomErrorCode expected
	) {
		when(store.findRoomMetadata(ROOM_ID)).thenReturn(Optional.of(metadata()));
		when(store.leaveAtomically(any())).thenReturn(result);

		assertThatThrownBy(() -> service.leaveByParticipantKey(ROOM_ID, "USER:1"))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(expected));
	}

	private WaitingRoomMetadata metadata() {
		return new WaitingRoomMetadata(
			ROOM_ID,
			RoomType.INVITE,
			RoomStatus.WAITING,
			"0123"
		);
	}
}
