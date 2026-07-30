package org.ssafy.b102.backend.waitingroom.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.game.entity.PlayMode;
import org.ssafy.b102.backend.game.service.GameService;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.CommonErrorCode;
import org.ssafy.b102.backend.waitingroom.config.WaitingRoomProperties;
import org.ssafy.b102.backend.waitingroom.dto.request.WaitingRoomCreateRequest;
import org.ssafy.b102.backend.waitingroom.dto.response.WaitingRoomCreateResponse;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
import org.ssafy.b102.backend.waitingroom.repository.CreateInviteRoomCommand;
import org.ssafy.b102.backend.waitingroom.repository.CreateInviteRoomResult;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomStore;
import org.ssafy.b102.backend.waitingroom.support.InviteCodeGenerator;
import org.ssafy.b102.backend.waitingroom.support.ResolvedWaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.support.RoomIdGenerator;
import org.ssafy.b102.backend.waitingroom.support.WaitingRoomParticipantResolver;

class WaitingRoomServiceTest {

	private static final Instant NOW = Instant.parse("2026-07-30T04:00:00Z");
	private static final UUID ROOM_ID =
		UUID.fromString("c93c76b2-7f78-4275-b8af-7cdd921bbb4f");

	private GameService gameService;
	private WaitingRoomParticipantResolver participantResolver;
	private WaitingRoomStore waitingRoomStore;
	private InviteCodeGenerator inviteCodeGenerator;
	private RoomIdGenerator roomIdGenerator;
	private WaitingRoomService service;

	@BeforeEach
	void setUp() {
		gameService = mock(GameService.class);
		participantResolver = mock(WaitingRoomParticipantResolver.class);
		waitingRoomStore = mock(WaitingRoomStore.class);
		inviteCodeGenerator = mock(InviteCodeGenerator.class);
		roomIdGenerator = mock(RoomIdGenerator.class);
		service = new WaitingRoomService(
			gameService,
			participantResolver,
			waitingRoomStore,
			new WaitingRoomProperties(
				Duration.ofMinutes(10),
				Duration.ofSeconds(30),
				2,
				20
			),
			inviteCodeGenerator,
			roomIdGenerator,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void createsInviteRoomWithHostDefaults() {
		when(gameService.supportsPlayMode(GameName.EYEFIGHT, PlayMode.INVITE))
			.thenReturn(true);
		when(participantResolver.resolve(null, null)).thenReturn(
			ResolvedWaitingRoomParticipant.guest(
				"GUEST:abc",
				"게스트닉네임",
				UUID.fromString("7e329e72-e8da-4c62-8282-754e7b5c0864")
			)
		);
		when(roomIdGenerator.generate()).thenReturn(ROOM_ID);
		when(inviteCodeGenerator.generate()).thenReturn("0123");
		when(waitingRoomStore.createInviteRoomAtomically(any()))
			.thenReturn(CreateInviteRoomResult.CREATED);

		WaitingRoomCreateResponse response =
			service.createInviteRoom(null, null, request("EYEFIGHT"));

		assertThat(response.roomId()).isEqualTo(ROOM_ID);
		assertThat(response.roomType()).isEqualTo("INVITE");
		assertThat(response.gameName()).isEqualTo("EYEFIGHT");
		assertThat(response.roomCode()).isEqualTo("0123");
		assertThat(response.roomStatus()).isEqualTo("WAITING");
		assertThat(response.createdAt()).isEqualTo(NOW);
		assertThat(response.participant().participantKey()).isEqualTo("GUEST:abc");
		assertThat(response.participant().roomRole()).isEqualTo("HOST");
		assertThat(response.participant().slotNo()).isEqualTo(1);
		assertThat(response.participant().isReady()).isFalse();
		assertThat(response.participant().calibrationStatus()).isEqualTo("PENDING");
		assertThat(response.participant().joinedAt()).isEqualTo(NOW);
	}

	@Test
	void invalidEnumDoesNotResolveGuestOrCallRedis() {
		assertError("STARING", WaitingRoomErrorCode.INVALID_GAME_NAME);
		assertError("eyefight", WaitingRoomErrorCode.INVALID_GAME_NAME);
		assertError("UNKNOWN", WaitingRoomErrorCode.INVALID_GAME_NAME);

		verify(participantResolver, never()).resolve(any(), any());
		verify(waitingRoomStore, never()).createInviteRoomAtomically(any());
	}

	@Test
	void inviteUnsupportedGameDoesNotResolveGuestOrCallRedis() {
		when(gameService.supportsPlayMode(GameName.BLINK, PlayMode.INVITE))
			.thenReturn(false);

		assertError("BLINK", WaitingRoomErrorCode.INVALID_GAME_NAME);

		verify(participantResolver, never()).resolve(any(), any());
		verify(waitingRoomStore, never()).createInviteRoomAtomically(any());
	}

	@Test
	void unknownFieldUsesMalformedJsonErrorBeforeSideEffects() {
		WaitingRoomCreateRequest request = request("EYEFIGHT");
		request.addUnknownField("roomStatus", "WAITING");

		assertThatThrownBy(() -> service.createInviteRoom(null, null, request))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.MALFORMED_JSON));
		verify(gameService, never()).supportsPlayMode(any(), any());
		verify(participantResolver, never()).resolve(any(), any());
	}

	@Test
	void retriesConflictThenReturnsCreatedRoom() {
		stubValidMember();
		when(inviteCodeGenerator.generate()).thenReturn("0000", "9999");
		when(waitingRoomStore.createInviteRoomAtomically(any()))
			.thenReturn(
				CreateInviteRoomResult.INVITE_CODE_CONFLICT,
				CreateInviteRoomResult.CREATED
			);

		WaitingRoomCreateResponse response =
			service.createInviteRoom(null, null, request("EYEFIGHT"));

		assertThat(response.roomCode()).isEqualTo("9999");
		verify(waitingRoomStore, times(2)).createInviteRoomAtomically(any());
	}

	@Test
	void twentyConflictsFailWithInviteCodeGenerationError() {
		stubValidMember();
		when(inviteCodeGenerator.generate()).thenReturn("0000");
		when(waitingRoomStore.createInviteRoomAtomically(any()))
			.thenReturn(CreateInviteRoomResult.INVITE_CODE_CONFLICT);

		assertError("EYEFIGHT", WaitingRoomErrorCode.INVITE_CODE_GENERATION_FAILED);

		verify(waitingRoomStore, times(20)).createInviteRoomAtomically(any());
	}

	private void stubValidMember() {
		when(gameService.supportsPlayMode(GameName.EYEFIGHT, PlayMode.INVITE))
			.thenReturn(true);
		when(participantResolver.resolve(null, null)).thenReturn(
			ResolvedWaitingRoomParticipant.member("USER:1", "회원닉네임")
		);
		when(roomIdGenerator.generate()).thenReturn(ROOM_ID);
	}

	private void assertError(String gameName, Object expectedErrorCode) {
		assertThatThrownBy(() -> service.createInviteRoom(null, null, request(gameName)))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode));
	}

	private WaitingRoomCreateRequest request(String gameName) {
		WaitingRoomCreateRequest request = new WaitingRoomCreateRequest();
		request.setGameName(gameName);
		return request;
	}
}
