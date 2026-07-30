package org.ssafy.b102.backend.waitingroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.service.GameService;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.CommonErrorCode;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.waitingroom.config.WaitingRoomProperties;
import org.ssafy.b102.backend.waitingroom.dto.request.WaitingRoomJoinRequest;
import org.ssafy.b102.backend.waitingroom.dto.response.WaitingRoomJoinResponse;
import org.ssafy.b102.backend.waitingroom.entity.CalibrationStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomRole;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
import org.ssafy.b102.backend.waitingroom.repository.JoinInviteRoomCommand;
import org.ssafy.b102.backend.waitingroom.repository.JoinInviteRoomResult;
import org.ssafy.b102.backend.waitingroom.repository.JoinInviteRoomResult.Status;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomSnapshot;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomStore;
import org.ssafy.b102.backend.waitingroom.support.InviteCodeGenerator;
import org.ssafy.b102.backend.waitingroom.support.ResolvedWaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.support.RoomIdGenerator;
import org.ssafy.b102.backend.waitingroom.support.WaitingRoomParticipantResolver;

class WaitingRoomJoinServiceTest {

	private static final UUID ROOM_ID =
		UUID.fromString("c93c76b2-7f78-4275-b8af-7cdd921bbb4f");
	private static final Instant CREATED_AT = Instant.parse("2026-07-30T04:00:00Z");
	private static final Instant JOINED_AT = Instant.parse("2026-07-30T04:01:00Z");

	private WaitingRoomParticipantResolver participantResolver;
	private WaitingRoomStore waitingRoomStore;
	private WaitingRoomService service;

	@BeforeEach
	void setUp() {
		participantResolver = mock(WaitingRoomParticipantResolver.class);
		waitingRoomStore = mock(WaitingRoomStore.class);
		service = new WaitingRoomService(
			mock(GameService.class),
			participantResolver,
			waitingRoomStore,
			new WaitingRoomProperties(
				Duration.ofMinutes(10),
				Duration.ofSeconds(30),
				2,
				20
			),
			mock(InviteCodeGenerator.class),
			mock(RoomIdGenerator.class),
			Clock.fixed(JOINED_AT, ZoneOffset.UTC)
		);
	}

	@Test
	void invalidInviteCodeFailsBeforeResolvingParticipant() {
		when(waitingRoomStore.findRoomIdByInviteCode("0123")).thenReturn(Optional.empty());

		assertError(Status.INVALID_INVITE_CODE, WaitingRoomErrorCode.INVALID_INVITE_CODE, false);

		verify(participantResolver, never()).resolve(any(), any());
		verify(waitingRoomStore, never()).joinInviteRoomAtomically(any());
	}

	@Test
	void unknownFieldFailsBeforeRedisAndResolver() {
		WaitingRoomJoinRequest request = request();
		request.addUnknownField("roomId", ROOM_ID);

		assertThatThrownBy(() -> service.joinInviteRoom(null, null, request))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.MALFORMED_JSON));
		verify(waitingRoomStore, never()).findRoomIdByInviteCode(any());
		verify(participantResolver, never()).resolve(any(), any());
	}

	@Test
	void joinedResultReturnsSortedSnapshotAndPlayerCommand() {
		UUID guestSessionId =
			UUID.fromString("7e329e72-e8da-4c62-8282-754e7b5c0864");
		when(waitingRoomStore.findRoomIdByInviteCode("0123"))
			.thenReturn(Optional.of(ROOM_ID));
		when(participantResolver.resolve(null, guestSessionId)).thenReturn(
			ResolvedWaitingRoomParticipant.guest(
				"GUEST:" + guestSessionId,
				"게스트닉네임",
				guestSessionId
			)
		);
		when(waitingRoomStore.joinInviteRoomAtomically(any()))
			.thenReturn(JoinInviteRoomResult.joined(snapshot()));

		WaitingRoomJoinResponse response =
			service.joinInviteRoom(null, guestSessionId, request());

		assertThat(response.roomId()).isEqualTo(ROOM_ID);
		assertThat(response.participants())
			.extracting(participant -> participant.slotNo())
			.containsExactly(1, 2);
		assertThat(response.guestSessionId()).isEqualTo(guestSessionId);
		assertThat(response.guestNickname()).isEqualTo("게스트닉네임");

		ArgumentCaptor<JoinInviteRoomCommand> captor =
			ArgumentCaptor.forClass(JoinInviteRoomCommand.class);
		verify(waitingRoomStore).joinInviteRoomAtomically(captor.capture());
		JoinInviteRoomCommand command = captor.getValue();
		assertThat(command.participantKey()).isEqualTo("GUEST:" + guestSessionId);
		assertThat(command.displayName()).isEqualTo("게스트닉네임");
		assertThat(command.joinedAt()).isEqualTo(JOINED_AT);
		assertThat(command.maxParticipants()).isEqualTo(2);
		assertThat(command.ttl()).isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	void memberResponseOmitsGuestFields() {
		AuthenticatedUser member = new AuthenticatedUser(1L);
		when(waitingRoomStore.findRoomIdByInviteCode("0123"))
			.thenReturn(Optional.of(ROOM_ID));
		when(participantResolver.resolve(member, null))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "회원닉네임"));
		when(waitingRoomStore.joinInviteRoomAtomically(any()))
			.thenReturn(JoinInviteRoomResult.joined(snapshot()));

		WaitingRoomJoinResponse response =
			service.joinInviteRoom(member, null, request());

		assertThat(response.guestSessionId()).isNull();
		assertThat(response.guestNickname()).isNull();
	}

	@Test
	void mapsAtomicJoinResultsToWaitingRoomErrors() {
		assertError(Status.INVALID_INVITE_CODE, WaitingRoomErrorCode.INVALID_INVITE_CODE, true);
		assertError(Status.NOT_JOINABLE, WaitingRoomErrorCode.WAITING_ROOM_NOT_JOINABLE, true);
		assertError(Status.ALREADY_JOINED, WaitingRoomErrorCode.PARTICIPANT_ALREADY_JOINED, true);
		assertError(Status.FULL, WaitingRoomErrorCode.WAITING_ROOM_FULL, true);
		assertError(Status.CORRUPTED, WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE, true);
	}

	private void assertError(
		Status status,
		WaitingRoomErrorCode expected,
		boolean existingIndex
	) {
		when(waitingRoomStore.findRoomIdByInviteCode("0123"))
			.thenReturn(existingIndex ? Optional.of(ROOM_ID) : Optional.empty());
		if (existingIndex) {
			when(participantResolver.resolve(null, null))
				.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "회원닉네임"));
			when(waitingRoomStore.joinInviteRoomAtomically(any()))
				.thenReturn(JoinInviteRoomResult.of(status));
		}

		assertThatThrownBy(() -> service.joinInviteRoom(null, null, request()))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(expected));
	}

	private WaitingRoomJoinRequest request() {
		WaitingRoomJoinRequest request = new WaitingRoomJoinRequest();
		request.setRoomCode("0123");
		return request;
	}

	private WaitingRoomSnapshot snapshot() {
		WaitingRoom room = new WaitingRoom(
			ROOM_ID,
			RoomType.INVITE,
			GameName.EYEFIGHT,
			"0123",
			RoomStatus.WAITING,
			CREATED_AT
		);
		WaitingRoomParticipant host = new WaitingRoomParticipant(
			"USER:1",
			"회원닉네임",
			RoomRole.HOST,
			1,
			false,
			CalibrationStatus.PENDING,
			CREATED_AT
		);
		WaitingRoomParticipant player = new WaitingRoomParticipant(
			"GUEST:test",
			"게스트닉네임",
			RoomRole.PLAYER,
			2,
			false,
			CalibrationStatus.PENDING,
			JOINED_AT
		);
		return new WaitingRoomSnapshot(room, List.of(player, host));
	}
}
