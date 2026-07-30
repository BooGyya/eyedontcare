package org.ssafy.b102.backend.waitingroom.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.waitingroom.entity.CalibrationStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomRole;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.repository.LeaveWaitingRoomResult;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomSnapshot;
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
	private WaitingRoomParticipantResolver participantResolver;
	private InMemoryWaitingRoomWebSocketSessionRegistry registry;
	private JwtTokenProvider jwtTokenProvider;
	private TaskScheduler taskScheduler;
	private WaitingRoomWebSocketService service;

	@BeforeEach
	void setUp() {
		waitingRoomService = mock(WaitingRoomService.class);
		participantResolver = mock(WaitingRoomParticipantResolver.class);
		registry = new InMemoryWaitingRoomWebSocketSessionRegistry();
		jwtTokenProvider = mock(JwtTokenProvider.class);
		taskScheduler = mock(TaskScheduler.class);
		when(taskScheduler.schedule(any(Runnable.class), any(Instant.class)))
			.thenReturn(mock(ScheduledFuture.class));
		service = new WaitingRoomWebSocketService(
			waitingRoomService,
			participantResolver,
			registry,
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
	void initialStateSendFailureUnregistersClosesAndLeaves() {
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
		StubWebSocketSession session = new FailingWebSocketSession("s1");

		authenticate(session);

		assertThat(registry.findBySessionId("s1")).isEmpty();
		assertThat(session.closeStatus()).isEqualTo(CloseStatus.SERVER_ERROR);
		verify(waitingRoomService).leaveByParticipantKey(ROOM_ID, "USER:1");
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
		when(waitingRoomService.leaveByParticipantKey(ROOM_ID, "USER:1"))
			.thenReturn(LeaveWaitingRoomResult.ROOM_CLOSED);
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

	private WaitingRoomSnapshot snapshot(RoomStatus status) {
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
}
