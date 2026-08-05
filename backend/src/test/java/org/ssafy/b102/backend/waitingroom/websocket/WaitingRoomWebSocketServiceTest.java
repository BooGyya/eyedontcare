package org.ssafy.b102.backend.waitingroom.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.waitingroom.entity.CalibrationStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomRole;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
import org.ssafy.b102.backend.waitingroom.repository.LeaveWaitingRoomResult;
import org.ssafy.b102.backend.waitingroom.repository.RandomRoomLeaveResult;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomSnapshot;
import org.ssafy.b102.backend.waitingroom.service.RandomRematchRequester;
import org.ssafy.b102.backend.waitingroom.service.RandomRematchRequestResult;
import org.ssafy.b102.backend.waitingroom.service.RandomRoomLifecyclePort;
import org.ssafy.b102.backend.waitingroom.service.WaitingRoomLeaveOutcome;
import org.ssafy.b102.backend.waitingroom.service.WaitingRoomCommandService;
import org.ssafy.b102.backend.waitingroom.service.WaitingRoomCommandService.StartCommandResult;
import org.ssafy.b102.backend.waitingroom.service.WaitingRoomService;
import org.ssafy.b102.backend.waitingroom.support.ResolvedWaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.support.WaitingRoomParticipantResolver;
import org.ssafy.b102.testfixture.websocket.StubWebSocketSession;
import tools.jackson.databind.json.JsonMapper;

class WaitingRoomWebSocketServiceTest {

	private static final UUID ROOM_ID =
		UUID.fromString("c93c76b2-7f78-4275-b8af-7cdd921bbb4f");
	private static final Instant NOW =
		Instant.parse("2026-07-30T04:00:00Z");

	private WaitingRoomService waitingRoomService;
	private WaitingRoomCommandService commandService;
	private WaitingRoomParticipantResolver participantResolver;
	private InMemoryWaitingRoomWebSocketSessionRegistry registry;
	private JwtTokenProvider jwtTokenProvider;
	private TaskScheduler taskScheduler;
	private WaitingRoomCountdownCoordinator countdownCoordinator;
	private WaitingRoomWebSocketService service;

	@BeforeEach
	void setUp() {
		waitingRoomService = mock(WaitingRoomService.class);
		commandService = mock(WaitingRoomCommandService.class);
		participantResolver = mock(WaitingRoomParticipantResolver.class);
		registry = new InMemoryWaitingRoomWebSocketSessionRegistry();
		jwtTokenProvider = mock(JwtTokenProvider.class);
		taskScheduler = mock(TaskScheduler.class);
		when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
			.thenReturn(mock(ScheduledFuture.class));
		countdownCoordinator =
			new WaitingRoomCountdownCoordinator(taskScheduler);
		service = new WaitingRoomWebSocketService(
			waitingRoomService,
			commandService,
			participantResolver,
			registry,
			countdownCoordinator,
			jwtTokenProvider,
			JsonMapper.builder().findAndAddModules().build(),
			new WaitingRoomWebSocketProperties(
				Duration.ofSeconds(5),
				Duration.ofSeconds(5),
				65536
			),
			taskScheduler,
			Clock.fixed(NOW, ZoneOffset.UTC)
		);
	}

	@Test
	void memberAuthRegistersAndSendsInitialRoomState() {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(
				ResolvedWaitingRoomParticipant.member("USER:1", "회원")
			);
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(snapshot(RoomStatus.WAITING));

		service.connectionEstablished(session, ROOM_ID);
		service.handleMessage(
			session,
			"{\"type\":\"AUTH\",\"accessToken\":\"token\"}"
		);

		assertThat(registry.findBySessionId("s1")).isPresent();
		assertThat(session.sentPayloads()).singleElement()
			.asString()
			.contains("\"type\":\"ROOM_STATE\"")
			.contains("\"roomId\":\"" + ROOM_ID + "\"")
			.doesNotContain("accessToken")
			.doesNotContain("guestSessionId");
	}

	@Test
	void invalidMemberTokenDoesNotFallBackToGuest() {
		StubWebSocketSession session = new StubWebSocketSession("s1");
		when(jwtTokenProvider.parseAccessTokenUserId("invalid"))
			.thenReturn(Optional.empty());

		service.connectionEstablished(session, ROOM_ID);
		service.handleMessage(
			session,
			"{\"type\":\"AUTH\",\"accessToken\":\"invalid\","
				+ "\"guestSessionId\":\"550e8400-e29b-41d4-a716-446655440000\"}"
		);

		assertThat(session.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION);
		assertThat(session.lastSentPayload()).contains("SECURITY-002");
		verify(participantResolver, never()).resolveExisting(any(), any());
	}

	@Test
	void duplicateConnectionIsRejectedWithoutReplacingExistingSession() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(
				ResolvedWaitingRoomParticipant.member("USER:1", "회원")
			);
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(snapshot(RoomStatus.WAITING));
		StubWebSocketSession first = new StubWebSocketSession("s1");
		StubWebSocketSession second = new StubWebSocketSession("s2");

		authenticate(first);
		authenticate(second);

		assertThat(registry.findBySessionId("s1")).isPresent();
		assertThat(registry.findBySessionId("s2")).isEmpty();
		assertThat(second.lastSentPayload()).contains("WAITING-010");
		assertThat(second.closeStatus()).isEqualTo(
			CloseStatus.POLICY_VIOLATION
		);
		verify(waitingRoomService, never())
			.leaveByParticipantKey(any(), any());
	}

	@Test
	void authTimeoutSendsErrorAndDoesNotLeave() {
		ArgumentCaptor<Runnable> timeout = ArgumentCaptor.forClass(
			Runnable.class
		);
		StubWebSocketSession session = new StubWebSocketSession("s1");

		service.connectionEstablished(session, ROOM_ID);
		verify(taskScheduler).schedule(timeout.capture(), any(Instant.class));
		timeout.getValue().run();

		assertThat(session.lastSentPayload()).contains("WAITING-012");
		assertThat(session.closeStatus()).isEqualTo(
			CloseStatus.POLICY_VIOLATION
		);
		assertThat(registry.findBySessionId("s1")).isEmpty();
		verify(waitingRoomService, never())
			.leaveByParticipantKey(any(), any());
	}

	@Test
	void disconnectUsesRegisteredParticipantKeyOnce() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(
				ResolvedWaitingRoomParticipant.member("USER:1", "회원")
			);
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(snapshot(RoomStatus.WAITING));
		when(waitingRoomService.leaveByParticipantKey(ROOM_ID, "USER:1"))
			.thenReturn(LeaveWaitingRoomResult.LEFT);
		StubWebSocketSession session = new StubWebSocketSession("s1");
		authenticate(session);

		service.connectionClosed(session);
		service.connectionClosed(session);

		verify(waitingRoomService).leaveByParticipantKey(ROOM_ID, "USER:1");
		assertThat(registry.findBySessionId("s1")).isEmpty();
	}

	@Test
	void initialStateSendFailureUnregistersClosesWithoutLeaveCleanup() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(
				ResolvedWaitingRoomParticipant.member("USER:1", "회원")
			);
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(snapshot(RoomStatus.WAITING));
		when(waitingRoomService.leaveByParticipantKey(ROOM_ID, "USER:1"))
			.thenReturn(LeaveWaitingRoomResult.LEFT);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useLifecycle(lifecycle);
		StubWebSocketSession session = new FailingWebSocketSession("s1");

		authenticate(session);

		assertThat(registry.findBySessionId("s1")).isEmpty();
		assertThat(session.closeStatus()).isEqualTo(CloseStatus.SERVER_ERROR);
		verify(waitingRoomService, never()).leaveByParticipantKey(ROOM_ID, "USER:1");
		verify(waitingRoomService, never()).leaveWithOutcomeByParticipantKey(ROOM_ID, "USER:1");
		verify(lifecycle, never()).cleanupFailedParticipant(any(), any());
	}

	@Test
	void randomInitialStateSendFailureKeepsMatchmakingEntry() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "PLAYER"));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(randomSnapshot(RoomStatus.WAITING));
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useLifecycle(lifecycle);
		StubWebSocketSession session = new FailingWebSocketSession("s1");

		authenticate(session);

		verify(lifecycle, never()).cleanupFailedParticipant(any(), any());
		verify(lifecycle, never()).cleanupParticipantAfterLeave(any(), any());
		verify(waitingRoomService, never()).leaveWithOutcomeByParticipantKey(any(), any());
		assertThat(session.closeStatus()).isEqualTo(CloseStatus.SERVER_ERROR);
	}

	@Test
	void hostDisconnectBroadcastsClosedAndClosesRemainingSessionNormally() {
		when(jwtTokenProvider.parseAccessTokenUserId("host"))
			.thenReturn(Optional.of(1L));
		when(jwtTokenProvider.parseAccessTokenUserId("player"))
			.thenReturn(Optional.of(2L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(
				ResolvedWaitingRoomParticipant.member("USER:1", "HOST"),
				ResolvedWaitingRoomParticipant.member("USER:2", "PLAYER")
			);
		WaitingRoomSnapshot waiting = fullSnapshot(RoomStatus.WAITING);
		WaitingRoomSnapshot closed = fullSnapshot(RoomStatus.CLOSED);
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(waiting, waiting, waiting, waiting, closed);
		when(waitingRoomService.leaveWithOutcomeByParticipantKey(ROOM_ID, "USER:1"))
			.thenReturn(inviteOutcome("USER:1", LeaveWaitingRoomResult.ROOM_CLOSED));
		StubWebSocketSession host = new StubWebSocketSession("host-session");
		StubWebSocketSession player =
			new StubWebSocketSession("player-session");

		authenticate(host, "host");
		authenticate(player, "player");
		service.connectionClosed(host);

		assertThat(player.lastSentPayload())
			.contains("\"type\":\"ROOM_STATE\"")
			.contains("\"roomStatus\":\"CLOSED\"");
		assertThat(player.closeStatus()).isEqualTo(CloseStatus.NORMAL);
		assertThat(registry.findByRoomId(ROOM_ID)).isEmpty();
		verify(waitingRoomService, never())
			.leaveByParticipantKey(ROOM_ID, "USER:2");
		verify(waitingRoomService)
			.leaveWithOutcomeByParticipantKey(ROOM_ID, "USER:1");
	}

	@Test
	void hostRestLeaveBroadcastsClosedAndClosesAllSessionsOnce() {
		StubWebSocketSession host = registerSession(
			"host-session",
			"USER:1",
			RoomRole.HOST
		);
		StubWebSocketSession player = registerSession(
			"player-session",
			"USER:2",
			RoomRole.PLAYER
		);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(inviteOutcome("USER:1", LeaveWaitingRoomResult.ROOM_CLOSED));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(fullSnapshot(RoomStatus.CLOSED));

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		);
		service.connectionClosed(host);
		service.connectionClosed(player);

		assertThat(host.lastSentPayload()).contains("\"roomStatus\":\"CLOSED\"");
		assertThat(player.lastSentPayload())
			.contains("\"roomStatus\":\"CLOSED\"")
			.contains("\"participantKey\":\"USER:2\"")
			.contains("\"roomRole\":\"PLAYER\"");
		assertThat(player.sentPayloads().stream()
			.filter(payload -> payload.contains("\"roomStatus\":\"CLOSED\""))
			.count()).isEqualTo(1);
		assertThat(host.closeStatus()).isEqualTo(CloseStatus.NORMAL);
		assertThat(player.closeStatus()).isEqualTo(CloseStatus.NORMAL);
		assertThat(registry.findByRoomId(ROOM_ID)).isEmpty();
		verify(waitingRoomService, never())
			.leaveWithOutcomeByParticipantKey(any(), any());
	}

	@Test
	void waitingGuestPlayerRestLeaveBroadcastsHostAndClosesOnlyPlayer() {
		UUID guestSessionId =
			UUID.fromString("e93c76b2-7f78-4275-b8af-7cdd921bbb4f");
		String guestParticipantKey = "GUEST:" + guestSessionId;
		StubWebSocketSession host = registerSession(
			"host-session",
			"USER:1",
			RoomRole.HOST
		);
		StubWebSocketSession player = registerSession(
			"player-session",
			guestParticipantKey,
			RoomRole.PLAYER
		);
		when(waitingRoomService.leave(ROOM_ID, null, guestSessionId))
			.thenReturn(inviteOutcome(guestParticipantKey, LeaveWaitingRoomResult.LEFT));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(snapshot(RoomStatus.WAITING));

		service.leaveFromRest(ROOM_ID, null, guestSessionId);
		service.connectionClosed(player);

		assertThat(host.lastSentPayload())
			.contains("\"roomStatus\":\"WAITING\"")
			.contains("\"participantKey\":\"USER:1\"")
			.contains("\"roomRole\":\"HOST\"")
			.doesNotContain(guestParticipantKey);
		assertThat(host.closeStatus()).isNull();
		assertThat(player.closeStatus()).isEqualTo(CloseStatus.NORMAL);
		assertThat(registry.findBySessionId("host-session")).isPresent();
		assertThat(registry.findBySessionId("player-session")).isEmpty();
		verify(waitingRoomService, never())
			.leaveWithOutcomeByParticipantKey(any(), any());
	}

	@Test
	void inviteCountdownRestLeaveCancelsTaskBroadcastsClosedAndClosesSessions() {
		@SuppressWarnings("rawtypes")
		ScheduledFuture future = mock(ScheduledFuture.class);
		when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(future);
		countdownCoordinator.scheduleIfAbsent(ROOM_ID, NOW.plusSeconds(3), () -> {});
		StubWebSocketSession host = registerSession(
			"host-session",
			"USER:1",
			RoomRole.HOST
		);
		StubWebSocketSession player = registerSession(
			"player-session",
			"USER:2",
			RoomRole.PLAYER
		);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(inviteOutcome("USER:2", LeaveWaitingRoomResult.ROOM_CLOSED));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(fullSnapshot(RoomStatus.CLOSED));

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(2L),
			null
		);

		verify(future).cancel(false);
		assertThat(host.lastSentPayload()).contains("\"roomStatus\":\"CLOSED\"");
		assertThat(player.lastSentPayload()).contains("\"roomStatus\":\"CLOSED\"");
		assertThat(host.sentPayloads()).noneMatch(payload -> payload.contains("GAME_START"));
		assertThat(player.sentPayloads()).noneMatch(payload -> payload.contains("GAME_START"));
		assertThat(host.closeStatus()).isEqualTo(CloseStatus.NORMAL);
		assertThat(player.closeStatus()).isEqualTo(CloseStatus.NORMAL);
	}

	@Test
	void alreadyClosedInviteRestLeaveDoesNotRepeatPostProcessing() {
		StubWebSocketSession player = registerSession(
			"player-session",
			"USER:2",
			RoomRole.PLAYER
		);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(inviteOutcome("USER:2", LeaveWaitingRoomResult.ALREADY_CLOSED));

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(2L),
			null
		);

		verify(waitingRoomService, never()).findSnapshot(ROOM_ID);
		assertThat(player.sentPayloads()).isEmpty();
		assertThat(player.closeStatus()).isNull();
		assertThat(registry.findBySessionId("player-session")).isPresent();
	}

	@Test
	void restLeaveAndDisconnectRaceBroadcastsClosedOnlyOnce() {
		StubWebSocketSession host = registerSession(
			"host-session",
			"USER:1",
			RoomRole.HOST
		);
		StubWebSocketSession player = registerSession(
			"player-session",
			"USER:2",
			RoomRole.PLAYER
		);
		when(waitingRoomService.leaveWithOutcomeByParticipantKey(ROOM_ID, "USER:1"))
			.thenReturn(inviteOutcome("USER:1", LeaveWaitingRoomResult.ALREADY_CLOSED));
		when(waitingRoomService.leave(any(), any(), any()))
			.thenAnswer(invocation -> {
				service.connectionClosed(host);
				return inviteOutcome("USER:1", LeaveWaitingRoomResult.ROOM_CLOSED);
			});
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(fullSnapshot(RoomStatus.CLOSED));

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		);

		assertThat(player.sentPayloads().stream()
			.filter(payload -> payload.contains("\"roomStatus\":\"CLOSED\""))
			.count()).isEqualTo(1);
		assertThat(player.closeStatus()).isEqualTo(CloseStatus.NORMAL);
		verify(waitingRoomService)
			.leaveWithOutcomeByParticipantKey(ROOM_ID, "USER:1");
	}

	@Test
	void inviteClosedBroadcastFailureStillClosesOtherSessions() {
		StubWebSocketSession failing = new FailingWebSocketSession("failing-session");
		registerSession(failing, "USER:1", RoomRole.HOST);
		StubWebSocketSession player = registerSession(
			"player-session",
			"USER:2",
			RoomRole.PLAYER
		);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(inviteOutcome("USER:1", LeaveWaitingRoomResult.ROOM_CLOSED));
		when(waitingRoomService.leaveWithOutcomeByParticipantKey(ROOM_ID, "USER:1"))
			.thenReturn(inviteOutcome("USER:1", LeaveWaitingRoomResult.ALREADY_CLOSED));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(fullSnapshot(RoomStatus.CLOSED));

		assertThatCode(() -> service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		)).doesNotThrowAnyException();

		assertThat(player.lastSentPayload()).contains("\"roomStatus\":\"CLOSED\"");
		assertThat(player.closeStatus()).isEqualTo(CloseStatus.NORMAL);
		assertThat(registry.findByRoomId(ROOM_ID)).isEmpty();
	}

	@Test
	void oneInviteSessionCloseFailureDoesNotBlockOtherSessionClose() {
		registerSession(
			new FailingCloseWebSocketSession("failing-session"),
			"USER:1",
			RoomRole.HOST
		);
		StubWebSocketSession player = registerSession(
			"player-session",
			"USER:2",
			RoomRole.PLAYER
		);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(inviteOutcome("USER:1", LeaveWaitingRoomResult.ROOM_CLOSED));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(fullSnapshot(RoomStatus.CLOSED));

		assertThatCode(() -> service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		)).doesNotThrowAnyException();

		assertThat(player.closeStatus()).isEqualTo(CloseStatus.NORMAL);
		assertThat(registry.findByRoomId(ROOM_ID)).isEmpty();
	}

	@Test
	void authenticatedCalibrationCommandUpdatesOwnParticipantAndBroadcasts() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "회원"));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(snapshot(RoomStatus.WAITING));
		when(
			commandService.updateCalibration(
				ROOM_ID,
				"USER:1",
				CalibrationStatus.IN_PROGRESS
			)
		).thenReturn(true);
		StubWebSocketSession session = new StubWebSocketSession("s1");
		authenticate(session);

		service.handleMessage(
			session,
			"{\"type\":\"CALIBRATION_STATUS\","
				+ "\"calibrationStatus\":\"IN_PROGRESS\"}"
		);

		verify(commandService).updateCalibration(
			ROOM_ID,
			"USER:1",
			CalibrationStatus.IN_PROGRESS
		);
		assertThat(session.lastSentPayload()).contains("\"type\":\"ROOM_STATE\"");
		assertThat(session.closeStatus()).isNull();
	}

	@Test
	void businessValidationErrorKeepsSocketOpen() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "회원"));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(snapshot(RoomStatus.WAITING));
		doThrow(new BusinessException(WaitingRoomErrorCode.CALIBRATION_REQUIRED))
			.when(commandService)
			.updateReady(ROOM_ID, "USER:1", true);
		StubWebSocketSession session = new StubWebSocketSession("s1");
		authenticate(session);

		service.handleMessage(
			session,
			"{\"type\":\"READY_STATUS\",\"isReady\":true}"
		);

		assertThat(session.lastSentPayload()).contains("WAITING-013");
		assertThat(session.closeStatus()).isNull();
		assertThat(registry.findBySessionId("s1")).isPresent();
	}

	@Test
	void randomRestLeaveRequeuesOnlyRemainingLiveParticipantAndClosesRoom() {
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<RandomRematchRequester> rematchProvider =
			mock(ObjectProvider.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<RandomRoomLifecyclePort> lifecycleProvider =
			mock(ObjectProvider.class);
		when(rematchProvider.getIfAvailable()).thenReturn(requester);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		when(lifecycleProvider.getIfAvailable()).thenReturn(lifecycle);
		doThrow(new IllegalStateException("redis unavailable"))
			.when(lifecycle).cleanupParticipantAfterLeave(ROOM_ID, "USER:1");
		when(
			requester.requeueRemaining(ROOM_ID, GameName.EYEFIGHT, "USER:2")
		).thenReturn(RandomRematchRequestResult.REQUEUED);
		RandomRoomLeaveResult leaveResult = new RandomRoomLeaveResult(
			RandomRoomLeaveResult.Status.CLOSED_NOW,
			ROOM_ID,
			GameName.EYEFIGHT,
			"USER:1",
			"USER:2",
			RoomStatus.WAITING
		);
		when(waitingRoomService.leave(ROOM_ID, new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L), null))
			.thenReturn(WaitingRoomLeaveOutcome.random(leaveResult));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(randomSnapshot(RoomStatus.CLOSED));
		StubWebSocketSession quitter = new StubWebSocketSession("s1");
		StubWebSocketSession remaining = new StubWebSocketSession("s2");
		registry.registerIfAbsent(
			new WaitingRoomConnectionContext(
				"s1", ROOM_ID, "USER:1", RoomRole.PLAYER, NOW, quitter
			)
		);
		registry.registerIfAbsent(
			new WaitingRoomConnectionContext(
				"s2", ROOM_ID, "USER:2", RoomRole.PLAYER, NOW, remaining
			)
		);
		service = new WaitingRoomWebSocketService(
			waitingRoomService,
			commandService,
			participantResolver,
			registry,
			countdownCoordinator,
			jwtTokenProvider,
			null,
			JsonMapper.builder().findAndAddModules().build(),
			new WaitingRoomWebSocketProperties(
				Duration.ofSeconds(5),
				Duration.ofSeconds(5),
				65536
			),
			taskScheduler,
			Clock.fixed(NOW, ZoneOffset.UTC),
			lifecycleProvider,
			rematchProvider
		);

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		);

		verify(requester).requeueRemaining(
			ROOM_ID,
			GameName.EYEFIGHT,
			"USER:2"
		);
		verify(lifecycle).cleanupParticipantAfterLeave(ROOM_ID, "USER:1");
		assertThat(remaining.lastSentPayload()).contains("\"roomStatus\":\"CLOSED\"");
		assertThat(quitter.closeStatus()).isEqualTo(CloseStatus.NORMAL);
		assertThat(remaining.closeStatus()).isEqualTo(CloseStatus.NORMAL);
		assertThat(registry.findByRoomId(ROOM_ID)).isEmpty();
		service.connectionClosed(quitter);
		service.connectionClosed(remaining);
		verify(requester).requeueRemaining(ROOM_ID, GameName.EYEFIGHT, "USER:2");
		verify(lifecycle, never()).cleanupRematchAfterPreviousRoomDisconnect(any(), any());
	}

	@Test
	void actualDisconnectAfterRematchCleansPreviousRoomEntryOnce() {
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useRandomDependencies(requester, lifecycle);
		StubWebSocketSession remaining = new StubWebSocketSession("remaining-session");
		registry.registerIfAbsent(
			new WaitingRoomConnectionContext(
				"remaining-session",
				ROOM_ID,
				"USER:2",
				RoomRole.PLAYER,
				NOW,
				remaining
			)
		);
		when(waitingRoomService.leaveWithOutcomeByParticipantKey(ROOM_ID, "USER:2"))
			.thenReturn(WaitingRoomLeaveOutcome.random(alreadyClosedResult()));

		service.connectionClosed(remaining);
		service.connectionClosed(remaining);

		verify(lifecycle, times(1)).cleanupRematchAfterPreviousRoomDisconnect(
			ROOM_ID,
			"USER:2"
		);
	}

	@Test
	void guestDisconnectAfterRematchUsesSameCleanup() {
		String guestKey = "GUEST:00000000-0000-0000-0000-000000000002";
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useRandomDependencies(requester, lifecycle);
		StubWebSocketSession remaining = new StubWebSocketSession("guest-session");
		registry.registerIfAbsent(
			new WaitingRoomConnectionContext(
				"guest-session",
				ROOM_ID,
				guestKey,
				RoomRole.PLAYER,
				NOW,
				remaining
			)
		);
		when(waitingRoomService.leaveWithOutcomeByParticipantKey(ROOM_ID, guestKey))
			.thenReturn(WaitingRoomLeaveOutcome.random(alreadyClosedResult()));

		service.connectionClosed(remaining);

		verify(lifecycle).cleanupRematchAfterPreviousRoomDisconnect(ROOM_ID, guestKey);
	}

	@Test
	void rematchCleanupFailureDoesNotFailDisconnectCallback() {
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useRandomDependencies(requester, lifecycle);
		StubWebSocketSession remaining = new StubWebSocketSession("remaining-session");
		registry.registerIfAbsent(
			new WaitingRoomConnectionContext(
				"remaining-session",
				ROOM_ID,
				"USER:2",
				RoomRole.PLAYER,
				NOW,
				remaining
			)
		);
		when(waitingRoomService.leaveWithOutcomeByParticipantKey(ROOM_ID, "USER:2"))
			.thenReturn(WaitingRoomLeaveOutcome.random(alreadyClosedResult()));
		doThrow(new IllegalStateException("redis unavailable"))
			.when(lifecycle)
			.cleanupRematchAfterPreviousRoomDisconnect(ROOM_ID, "USER:2");

		assertThatCode(() -> service.connectionClosed(remaining)).doesNotThrowAnyException();
		assertThat(registry.findBySessionId("remaining-session")).isEmpty();
	}

	@Test
	void disconnectBetweenRequeueAndServerCloseTriggersRematchCleanup() {
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useRandomDependencies(requester, lifecycle);
		StubWebSocketSession remaining = new StubWebSocketSession("remaining-session");
		registry.registerIfAbsent(
			new WaitingRoomConnectionContext(
				"remaining-session",
				ROOM_ID,
				"USER:2",
				RoomRole.PLAYER,
				NOW,
				remaining
			)
		);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(WaitingRoomLeaveOutcome.random(closedResult(RoomStatus.WAITING)));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(randomSnapshot(RoomStatus.CLOSED));
		when(waitingRoomService.leaveWithOutcomeByParticipantKey(ROOM_ID, "USER:2"))
			.thenReturn(WaitingRoomLeaveOutcome.random(alreadyClosedResult()));
		doAnswer(invocation -> {
			remaining.close();
			service.connectionClosed(remaining);
			return RandomRematchRequestResult.REQUEUED;
		}).when(requester).requeueRemaining(ROOM_ID, GameName.EYEFIGHT, "USER:2");

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		);

		verify(lifecycle, atLeastOnce()).cleanupRematchAfterPreviousRoomDisconnect(
			ROOM_ID,
			"USER:2"
		);
		assertThat(registry.findBySessionId("remaining-session")).isEmpty();
	}

	@Test
	void randomLeaveWithoutRemainingOpenSessionDoesNotRequeue() {
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useRandomDependencies(requester, lifecycle);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(WaitingRoomLeaveOutcome.random(closedResult(RoomStatus.WAITING)));
		when(waitingRoomService.findSnapshot(ROOM_ID)).thenReturn(randomSnapshot(RoomStatus.CLOSED));

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		);

		verify(requester, never()).requeueRemaining(any(), any(), any());
		verify(lifecycle).cleanupParticipantAfterLeave(ROOM_ID, "USER:1");
	}

	@Test
	void randomLeaveWithClosedRemainingSessionDoesNotRequeue() {
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useRandomDependencies(requester, lifecycle);
		StubWebSocketSession remaining = new StubWebSocketSession("remaining-session");
		remaining.close();
		registry.registerIfAbsent(
			new WaitingRoomConnectionContext(
				"remaining-session",
				ROOM_ID,
				"USER:2",
				RoomRole.PLAYER,
				NOW,
				remaining
			)
		);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(WaitingRoomLeaveOutcome.random(closedResult(RoomStatus.WAITING)));
		when(waitingRoomService.findSnapshot(ROOM_ID)).thenReturn(randomSnapshot(RoomStatus.CLOSED));

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		);

		verify(requester, never()).requeueRemaining(any(), any(), any());
	}

	@Test
	void randomLeaveWithParticipantSnapshotMismatchDoesNotRequeue() {
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useRandomDependencies(requester, lifecycle);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(WaitingRoomLeaveOutcome.random(closedResult(RoomStatus.WAITING)));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(randomSnapshotWithKeys(RoomStatus.CLOSED, List.of("USER:1", "USER:3")));

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		);

		verify(requester, never()).requeueRemaining(any(), any(), any());
	}

	@Test
	void randomLeaveWithCorruptedParticipantCountDoesNotRequeue() {
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useRandomDependencies(requester, lifecycle);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(WaitingRoomLeaveOutcome.random(closedResult(RoomStatus.WAITING)));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(randomSnapshotWithKeys(
				RoomStatus.CLOSED,
				List.of("USER:1", "USER:2", "USER:3")
			));

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		);

		verify(requester, never()).requeueRemaining(any(), any(), any());
	}

	@Test
	void randomLeaveWithEmptyParticipantSnapshotDoesNotRequeue() {
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useRandomDependencies(requester, lifecycle);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(WaitingRoomLeaveOutcome.random(closedResult(RoomStatus.WAITING)));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(randomSnapshotWithKeys(RoomStatus.CLOSED, List.of()));

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		);

		verify(requester, never()).requeueRemaining(any(), any(), any());
	}

	@Test
	void randomLeaveSnapshotFailureDoesNotRequeue() {
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useRandomDependencies(requester, lifecycle);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(WaitingRoomLeaveOutcome.random(closedResult(RoomStatus.WAITING)));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenThrow(new IllegalStateException("redis timeout"));

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		);

		verify(requester, never()).requeueRemaining(any(), any(), any());
		verify(lifecycle).cleanupParticipantAfterLeave(ROOM_ID, "USER:1");
	}

	@Test
	void alreadyClosedRandomLeaveIsNoOp() {
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useRandomDependencies(requester, lifecycle);
		RandomRoomLeaveResult alreadyClosed = new RandomRoomLeaveResult(
			RandomRoomLeaveResult.Status.ALREADY_CLOSED,
			ROOM_ID,
			GameName.EYEFIGHT,
			"USER:1",
			"USER:2",
			RoomStatus.WAITING
		);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(WaitingRoomLeaveOutcome.random(alreadyClosed));

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		);

		verify(waitingRoomService, never()).findSnapshot(ROOM_ID);
		verify(requester, never()).requeueRemaining(any(), any(), any());
		verify(lifecycle, never()).cleanupParticipantAfterLeave(any(), any());
		verify(lifecycle, never()).cleanupRematchAfterPreviousRoomDisconnect(any(), any());
	}

	@Test
	void randomCountdownLeaveCancelsScheduledTaskAndDoesNotStartGame() {
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useRandomDependencies(requester, lifecycle);
		@SuppressWarnings("rawtypes")
		ScheduledFuture future = mock(ScheduledFuture.class);
		when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(future);
		countdownCoordinator.scheduleIfAbsent(ROOM_ID, NOW.plusSeconds(3), () ->	{});
		registry.registerIfAbsent(
			new WaitingRoomConnectionContext(
				"remaining-session",
				ROOM_ID,
				"USER:2",
				RoomRole.PLAYER,
				NOW,
				new StubWebSocketSession("remaining-session")
			)
		);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenReturn(WaitingRoomLeaveOutcome.random(closedResult(RoomStatus.COUNTDOWN)));
		when(waitingRoomService.findSnapshot(ROOM_ID)).thenReturn(randomSnapshot(RoomStatus.CLOSED));

		service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		);

		verify(future).cancel(false);
		verify(requester).requeueRemaining(ROOM_ID, GameName.EYEFIGHT, "USER:2");
		verify(commandService, never()).completeCountdown(any(), any(), any(), any());
	}

	@Test
	void inGameLeaveDoesNotInvokeRematch() {
		RandomRematchRequester requester = mock(RandomRematchRequester.class);
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useRandomDependencies(requester, lifecycle);
		when(waitingRoomService.leave(any(), any(), any()))
			.thenThrow(new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_NOT_JOINABLE));

		assertThatThrownBy(() -> service.leaveFromRest(
			ROOM_ID,
			new org.ssafy.b102.backend.global.security.AuthenticatedUser(1L),
			null
		)).isInstanceOf(BusinessException.class);

		verify(requester, never()).requeueRemaining(any(), any(), any());
		verify(lifecycle, never()).cleanupParticipantAfterLeave(any(), any());
		verify(lifecycle, never()).cleanupRematchAfterPreviousRoomDisconnect(any(), any());
	}

	@Test
	void randomAuthenticationFailureCleansMatchingEntry() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "PLAYER"));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(randomSnapshot(RoomStatus.CLOSED));
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useLifecycle(lifecycle);
		StubWebSocketSession session = new StubWebSocketSession("s1");

		authenticate(session);

		verify(lifecycle).cleanupFailedParticipant(ROOM_ID, "USER:1");
		assertThat(session.lastSentPayload()).contains("WAITING-007");
	}

	@Test
	void missingWaitingRoomCleansMatchingEntry() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "PLAYER"));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenThrow(new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND));
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useLifecycle(lifecycle);
		StubWebSocketSession session = new StubWebSocketSession("s1");

		authenticate(session);

		verify(lifecycle).cleanupFailedParticipant(ROOM_ID, "USER:1");
		assertThat(session.lastSentPayload()).contains("WAITING-008");
	}

	@Test
	void missingParticipantCleansMatchingEntry() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:3", "PLAYER"));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(randomSnapshot(RoomStatus.WAITING));
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useLifecycle(lifecycle);
		StubWebSocketSession session = new StubWebSocketSession("s1");

		authenticate(session);

		verify(lifecycle).cleanupFailedParticipant(ROOM_ID, "USER:3");
		assertThat(session.lastSentPayload()).contains("WAITING-009");
	}

	@Test
	void waitingRoomStoreFailureDoesNotCleanupMatchingEntry() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "PLAYER"));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenThrow(new BusinessException(WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE));
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useLifecycle(lifecycle);
		StubWebSocketSession session = new StubWebSocketSession("s1");

		authenticate(session);

		verify(lifecycle, never()).cleanupFailedParticipant(any(), any());
		assertThat(session.lastSentPayload()).contains("WAITING-003");
	}

	@Test
	void unexpectedAuthenticationRuntimeDoesNotCleanupMatchingEntry() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "PLAYER"));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenThrow(new IllegalStateException("redis timeout"));
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useLifecycle(lifecycle);
		StubWebSocketSession session = new StubWebSocketSession("s1");

		authenticate(session);

		verify(lifecycle, never()).cleanupFailedParticipant(any(), any());
		assertThat(session.lastSentPayload()).contains("WAITING-003");
	}

	@Test
	void inviteAuthenticationFailureDoesNotCallMatchmakingCleanup() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "HOST"));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(snapshot(RoomStatus.CLOSED));
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		useLifecycle(lifecycle);
		StubWebSocketSession session = new StubWebSocketSession("s1");

		authenticate(session);

		verify(lifecycle, never()).cleanupFailedParticipant(any(), any());
		assertThat(session.lastSentPayload()).contains("WAITING-007");
	}

	@Test
	void cleanupFailureDoesNotReplaceOriginalAuthenticationError() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "PLAYER"));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(randomSnapshot(RoomStatus.CLOSED));
		RandomRoomLifecyclePort lifecycle = mock(RandomRoomLifecyclePort.class);
		doThrow(new IllegalStateException("cleanup failed"))
			.when(lifecycle).cleanupFailedParticipant(ROOM_ID, "USER:1");
		useLifecycle(lifecycle);
		StubWebSocketSession session = new StubWebSocketSession("s1");

		authenticate(session);

		assertThat(session.lastSentPayload()).contains("WAITING-007");
		assertThat(session.closeStatus()).isEqualTo(CloseStatus.POLICY_VIOLATION);
	}

	@Test
	void malformedCommandAfterAuthenticationClosesWithPolicyViolation() {
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "회원"));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(snapshot(RoomStatus.WAITING));
		StubWebSocketSession session = new StubWebSocketSession("s1");
		authenticate(session);

		service.handleMessage(
			session,
			"{\"type\":\"START_GAME\",\"participantKey\":\"USER:2\"}"
		);

		assertThat(session.lastSentPayload()).contains("WAITING-011");
		assertThat(session.closeStatus())
			.isEqualTo(CloseStatus.POLICY_VIOLATION);
	}

	@Test
	void countdownCompletionSendsGameStartAndClosesWithoutLeave() {
		Instant endsAt = NOW.plusSeconds(3);
		UUID countdownId =
			UUID.fromString("d93c76b2-7f78-4275-b8af-7cdd921bbb4f");
		when(jwtTokenProvider.parseAccessTokenUserId("token"))
			.thenReturn(Optional.of(1L));
		when(participantResolver.resolveExisting(any(), eq(null)))
			.thenReturn(ResolvedWaitingRoomParticipant.member("USER:1", "회원"));
		when(waitingRoomService.findSnapshot(ROOM_ID))
			.thenReturn(
				snapshot(RoomStatus.WAITING),
				snapshot(RoomStatus.WAITING),
				snapshot(RoomStatus.COUNTDOWN),
				snapshot(RoomStatus.IN_GAME)
			);
		when(commandService.startGame(ROOM_ID, "USER:1"))
			.thenReturn(
				new StartCommandResult(true, "0123", countdownId, endsAt)
			);
		when(
			commandService.completeCountdown(
				ROOM_ID,
				"0123",
				countdownId,
				endsAt
			)
		).thenReturn(true);
		StubWebSocketSession session = new StubWebSocketSession("s1");
		authenticate(session);
		ArgumentCaptor<Runnable> completion =
			ArgumentCaptor.forClass(Runnable.class);
		when(taskScheduler.schedule(completion.capture(), eq(endsAt)))
			.thenReturn(mock(ScheduledFuture.class));

		service.handleMessage(session, "{\"type\":\"START_GAME\"}");
		completion.getValue().run();

		assertThat(session.sentPayloads())
			.anySatisfy(payload -> assertThat(payload).contains("\"type\":\"GAME_START\""));
		assertThat(session.closeStatus()).isEqualTo(CloseStatus.NORMAL);
		assertThat(registry.findBySessionId("s1")).isEmpty();
		verify(waitingRoomService, never())
			.leaveByParticipantKey(ROOM_ID, "USER:1");
	}

	private void authenticate(StubWebSocketSession session) {
		authenticate(session, "token");
	}

	private void authenticate(
		StubWebSocketSession session,
		String token
	) {
		service.connectionEstablished(session, ROOM_ID);
		service.handleMessage(
			session,
			"{\"type\":\"AUTH\",\"accessToken\":\"" + token + "\"}"
		);
	}

	private void useLifecycle(RandomRoomLifecyclePort lifecycle) {
		@SuppressWarnings("unchecked")
		ObjectProvider<RandomRoomLifecyclePort> lifecycleProvider =
			mock(ObjectProvider.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<RandomRematchRequester> rematchProvider =
			mock(ObjectProvider.class);
		when(lifecycleProvider.getIfAvailable()).thenReturn(lifecycle);
		when(rematchProvider.getIfAvailable()).thenReturn(null);
		service = new WaitingRoomWebSocketService(
			waitingRoomService,
			commandService,
			participantResolver,
			registry,
			countdownCoordinator,
			jwtTokenProvider,
			null,
			JsonMapper.builder().findAndAddModules().build(),
			new WaitingRoomWebSocketProperties(
				Duration.ofSeconds(5),
				Duration.ofSeconds(5),
				65536
			),
			taskScheduler,
			Clock.fixed(NOW, ZoneOffset.UTC),
			lifecycleProvider,
			rematchProvider
		);
	}

	private void useRandomDependencies(
		RandomRematchRequester requester,
		RandomRoomLifecyclePort lifecycle
	) {
		@SuppressWarnings("unchecked")
		ObjectProvider<RandomRoomLifecyclePort> lifecycleProvider = mock(ObjectProvider.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<RandomRematchRequester> rematchProvider = mock(ObjectProvider.class);
		when(lifecycleProvider.getIfAvailable()).thenReturn(lifecycle);
		when(rematchProvider.getIfAvailable()).thenReturn(requester);
		service = new WaitingRoomWebSocketService(
			waitingRoomService,
			commandService,
			participantResolver,
			registry,
			countdownCoordinator,
			jwtTokenProvider,
			null,
			JsonMapper.builder().findAndAddModules().build(),
			new WaitingRoomWebSocketProperties(
				Duration.ofSeconds(5),
				Duration.ofSeconds(5),
				65536
			),
			taskScheduler,
			Clock.fixed(NOW, ZoneOffset.UTC),
			lifecycleProvider,
			rematchProvider
		);
	}

	private RandomRoomLeaveResult closedResult(RoomStatus previousStatus) {
		return new RandomRoomLeaveResult(
			RandomRoomLeaveResult.Status.CLOSED_NOW,
			ROOM_ID,
			GameName.EYEFIGHT,
			"USER:1",
			"USER:2",
			previousStatus
		);
	}

	private RandomRoomLeaveResult alreadyClosedResult() {
		return new RandomRoomLeaveResult(
			RandomRoomLeaveResult.Status.ALREADY_CLOSED,
			ROOM_ID,
			GameName.EYEFIGHT,
			"USER:1",
			"USER:2",
			RoomStatus.WAITING
		);
	}

	private WaitingRoomLeaveOutcome inviteOutcome(
		String participantKey,
		LeaveWaitingRoomResult result
	) {
		return WaitingRoomLeaveOutcome.invite(ROOM_ID, participantKey, result);
	}

	private StubWebSocketSession registerSession(
		String sessionId,
		String participantKey,
		RoomRole roomRole
	) {
		StubWebSocketSession session = new StubWebSocketSession(sessionId);
		registerSession(session, participantKey, roomRole);
		return session;
	}

	private void registerSession(
		StubWebSocketSession session,
		String participantKey,
		RoomRole roomRole
	) {
		assertThat(registry.registerIfAbsent(
			new WaitingRoomConnectionContext(
				session.getId(),
				ROOM_ID,
				participantKey,
				roomRole,
				NOW,
				session
			)
		)).isTrue();
	}

	private WaitingRoomSnapshot snapshot(RoomStatus status) {
		UUID countdownId = status == RoomStatus.COUNTDOWN
			? UUID.fromString("d93c76b2-7f78-4275-b8af-7cdd921bbb4f")
			: null;
		Instant countdownEndsAt = status == RoomStatus.COUNTDOWN
			? NOW.plusSeconds(3)
			: null;
		return new WaitingRoomSnapshot(
			new WaitingRoom(
				ROOM_ID,
				RoomType.INVITE,
				GameName.EYEFIGHT,
				"0123",
				status,
				NOW,
				countdownId,
				countdownEndsAt
			),
			List.of(
				new WaitingRoomParticipant(
					"USER:1",
					"회원",
					RoomRole.HOST,
					1,
					false,
					CalibrationStatus.PENDING,
					NOW
				)
			)
		);
	}

	private WaitingRoomSnapshot fullSnapshot(RoomStatus status) {
		return new WaitingRoomSnapshot(
			new WaitingRoom(
				ROOM_ID,
				RoomType.INVITE,
				GameName.EYEFIGHT,
				"0123",
				status,
				NOW
			),
			List.of(
				new WaitingRoomParticipant(
					"USER:1",
					"HOST",
					RoomRole.HOST,
					1,
					false,
					CalibrationStatus.PENDING,
					NOW
				),
				new WaitingRoomParticipant(
					"USER:2",
					"PLAYER",
					RoomRole.PLAYER,
					2,
					false,
					CalibrationStatus.PENDING,
					NOW
				)
			)
		);
	}

	private WaitingRoomSnapshot randomSnapshot(RoomStatus status) {
		return randomSnapshotWithKeys(status, List.of("USER:1", "USER:2"));
	}

	private WaitingRoomSnapshot randomSnapshotWithKeys(RoomStatus status, List<String> keys) {
		return new WaitingRoomSnapshot(
			new WaitingRoom(
				ROOM_ID,
				RoomType.RANDOM,
				GameName.EYEFIGHT,
				null,
				status,
				NOW
			),
			keys.stream()
				.map(key -> new WaitingRoomParticipant(
					key,
					key,
					RoomRole.PLAYER,
					keys.indexOf(key) + 1,
					false,
					CalibrationStatus.PENDING,
					NOW
				))
			.toList()
		);
	}

	private static final class FailingWebSocketSession
		extends StubWebSocketSession {

		private FailingWebSocketSession(String id) {
			super(id);
		}

		@Override
		public void sendMessage(WebSocketMessage<?> message) {
			throw new IllegalStateException("send failed");
		}
	}

	private static final class FailingCloseWebSocketSession
		extends StubWebSocketSession {

		private FailingCloseWebSocketSession(String id) {
			super(id);
		}

		@Override
		public void close(CloseStatus status) {
			throw new IllegalStateException("close failed");
		}
	}
}
