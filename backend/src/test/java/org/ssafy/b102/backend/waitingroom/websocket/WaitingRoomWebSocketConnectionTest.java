package org.ssafy.b102.backend.waitingroom.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.ssafy.b102.backend.game.entity.GameName;
import org.ssafy.b102.backend.guest.entity.GuestSession;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.guest.support.GuestParticipantKey;
import org.ssafy.b102.backend.waitingroom.entity.CalibrationStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomRole;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoom;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.repository.CreateInviteRoomCommand;
import org.ssafy.b102.backend.waitingroom.repository.CreateInviteRoomResult;
import org.ssafy.b102.backend.waitingroom.repository.JoinInviteRoomCommand;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomStore;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WaitingRoomWebSocketConnectionTest {

	private static final UUID ROOM_ID =
		UUID.fromString("a93c76b2-7f78-4275-b8af-7cdd921bbb4f");
	private static final String ROOM_KEY =
		"edc:test:waiting-room:room:" + ROOM_ID;
	private static final String PARTICIPANTS_KEY =
		"edc:test:waiting-room:participants:" + ROOM_ID;
	private static final String INVITE_KEY =
		"edc:test:waiting-room:invite-code:9876";

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private WaitingRoomStore waitingRoomStore;

	@Autowired
	private GuestSessionService guestSessionService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@AfterEach
	void tearDown() {
		redisTemplate.delete(java.util.List.of(
			ROOM_KEY,
			PARTICIPANTS_KEY,
			INVITE_KEY
		));
	}

	@Test
	void guestConnectsReceivesInitialStateAndDisconnectClosesHostedRoom()
		throws Exception {

		GuestSession guest = guestSessionService.issue();
		String participantKey =
			new GuestParticipantKey(guest.guestSessionId()).value();
		Instant now = Instant.parse("2026-07-30T04:00:00Z");
		assertThat(
			waitingRoomStore.createInviteRoomAtomically(
				new CreateInviteRoomCommand(
					new WaitingRoom(
						ROOM_ID,
						RoomType.INVITE,
						GameName.EYEFIGHT,
						"9876",
						RoomStatus.WAITING,
						now
					),
					new WaitingRoomParticipant(
						participantKey,
						guest.nickname(),
						RoomRole.HOST,
						1,
						false,
						CalibrationStatus.PENDING,
						now
					),
					Duration.ofMinutes(10)
				)
			)
		).isEqualTo(CreateInviteRoomResult.CREATED);

		CountDownLatch roomStateReceived = new CountDownLatch(1);
		CountDownLatch closed = new CountDownLatch(1);
		AtomicReference<String> payload = new AtomicReference<>();
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.add(HttpHeaders.ORIGIN, "http://localhost:5173");
		WebSocketSession session = new StandardWebSocketClient().execute(
			new TextWebSocketHandler() {
				@Override
				public void afterConnectionEstablished(
					WebSocketSession session
				) throws Exception {
					session.sendMessage(new TextMessage(
						"{\"type\":\"AUTH\",\"guestSessionId\":\""
							+ guest.guestSessionId() + "\"}"
					));
				}

				@Override
				protected void handleTextMessage(
					WebSocketSession session,
					TextMessage message
				) {
					payload.set(message.getPayload());
					roomStateReceived.countDown();
				}

				@Override
				public void afterConnectionClosed(
					WebSocketSession session,
					CloseStatus status
				) {
					closed.countDown();
				}
			},
			headers,
			URI.create(
				"ws://localhost:" + port + "/ws/waiting-rooms/" + ROOM_ID
			)
		).get(5, TimeUnit.SECONDS);

		assertThat(roomStateReceived.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(payload.get())
			.contains("\"type\":\"ROOM_STATE\"")
			.contains("\"roomStatus\":\"WAITING\"")
			.contains("\"participantKey\":\"" + participantKey + "\"");

		session.close();
		assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
		awaitRoomStatus(RoomStatus.CLOSED);
	}

	@Test
	void disallowedOriginCannotHandshake() {
		StandardWebSocketClient client = new StandardWebSocketClient();
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.add(HttpHeaders.ORIGIN, "http://evil.example.com");

		assertThat(
			client.execute(
				new TextWebSocketHandler(),
				headers,
				URI.create(
					"ws://localhost:" + port
						+ "/ws/waiting-rooms/" + ROOM_ID
				)
			)
		).failsWithin(Duration.ofSeconds(5));
	}

	@Test
	void inviteRoomCommandsReachGameStartAndCloseNormally() throws Exception {
		GuestSession hostGuest = guestSessionService.issue();
		GuestSession playerGuest = guestSessionService.issue();
		String hostKey =
			new GuestParticipantKey(hostGuest.guestSessionId()).value();
		String playerKey =
			new GuestParticipantKey(playerGuest.guestSessionId()).value();
		Instant now = Instant.parse("2026-07-30T04:00:00Z");
		assertThat(
			waitingRoomStore.createInviteRoomAtomically(
				new CreateInviteRoomCommand(
					new WaitingRoom(
						ROOM_ID,
						RoomType.INVITE,
						GameName.EYEFIGHT,
						"9876",
						RoomStatus.WAITING,
						now
					),
					new WaitingRoomParticipant(
						hostKey,
						hostGuest.nickname(),
						RoomRole.HOST,
						1,
						false,
						CalibrationStatus.PENDING,
						now
					),
					Duration.ofMinutes(10)
				)
			)
		).isEqualTo(CreateInviteRoomResult.CREATED);
		assertThat(
			waitingRoomStore.joinInviteRoomAtomically(
				new JoinInviteRoomCommand(
					ROOM_ID,
					"9876",
					playerKey,
					playerGuest.nickname(),
					now.plusSeconds(1),
					2,
					Duration.ofMinutes(10)
				)
			).status()
		).isEqualTo(
			org.ssafy.b102.backend.waitingroom.repository.JoinInviteRoomResult.Status.JOINED
		);

		RecordingHandler hostHandler = new RecordingHandler();
		RecordingHandler playerHandler = new RecordingHandler();
		WebSocketSession host = connect(hostHandler);
		WebSocketSession player = connect(playerHandler);
		host.sendMessage(new TextMessage(
			"{\"type\":\"AUTH\",\"guestSessionId\":\""
				+ hostGuest.guestSessionId() + "\"}"
		));
		player.sendMessage(new TextMessage(
			"{\"type\":\"AUTH\",\"guestSessionId\":\""
				+ playerGuest.guestSessionId() + "\"}"
		));
		hostHandler.await("\"roomStatus\":\"WAITING\"");
		playerHandler.await("\"roomStatus\":\"WAITING\"");

		sendAndAwaitBoth(
			host,
			"{\"type\":\"CALIBRATION_STATUS\","
				+ "\"calibrationStatus\":\"IN_PROGRESS\"}",
			hostHandler,
			playerHandler
		);
		sendAndAwaitBoth(
			host,
			"{\"type\":\"CALIBRATION_STATUS\","
				+ "\"calibrationStatus\":\"COMPLETED\"}",
			hostHandler,
			playerHandler
		);
		sendAndAwaitBoth(
			player,
			"{\"type\":\"CALIBRATION_STATUS\","
				+ "\"calibrationStatus\":\"IN_PROGRESS\"}",
			hostHandler,
			playerHandler
		);
		sendAndAwaitBoth(
			player,
			"{\"type\":\"CALIBRATION_STATUS\","
				+ "\"calibrationStatus\":\"COMPLETED\"}",
			hostHandler,
			playerHandler
		);
		sendAndAwaitBoth(
			player,
			"{\"type\":\"READY_STATUS\",\"isReady\":true}",
			hostHandler,
			playerHandler
		);
		host.sendMessage(new TextMessage("{\"type\":\"START_GAME\"}"));
		hostHandler.await("\"roomStatus\":\"COUNTDOWN\"");
		playerHandler.await("\"roomStatus\":\"COUNTDOWN\"");

		assertThat(hostHandler.await("\"type\":\"GAME_START\""))
			.contains("\"gameName\":\"EYEFIGHT\"")
			.doesNotContain("gameSessionId")
			.doesNotContain("countdownId");
		assertThat(playerHandler.await("\"type\":\"GAME_START\""))
			.contains("\"gameName\":\"EYEFIGHT\"");
		assertThat(hostHandler.closed.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(playerHandler.closed.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(hostHandler.closeStatus.get()).isEqualTo(CloseStatus.NORMAL);
		assertThat(playerHandler.closeStatus.get()).isEqualTo(CloseStatus.NORMAL);
		assertThat(waitingRoomStore.findSnapshot(ROOM_ID).orElseThrow()
			.room().roomStatus()).isEqualTo(RoomStatus.IN_GAME);
		assertThat(redisTemplate.hasKey(INVITE_KEY)).isFalse();
	}

	private WebSocketSession connect(RecordingHandler handler) throws Exception {
		WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
		headers.add(HttpHeaders.ORIGIN, "http://localhost:5173");
		return new StandardWebSocketClient().execute(
			handler,
			headers,
			URI.create(
				"ws://localhost:" + port + "/ws/waiting-rooms/" + ROOM_ID
			)
		).get(5, TimeUnit.SECONDS);
	}

	private void sendAndAwaitBoth(
		WebSocketSession sender,
		String payload,
		RecordingHandler first,
		RecordingHandler second
	) throws Exception {
		sender.sendMessage(new TextMessage(payload));
		first.await("\"type\":\"ROOM_STATE\"");
		second.await("\"type\":\"ROOM_STATE\"");
	}

	private void awaitRoomStatus(RoomStatus expected) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			if (
				waitingRoomStore.findSnapshot(ROOM_ID)
					.map(snapshot -> snapshot.room().roomStatus())
					.filter(expected::equals)
					.isPresent()
			) {
				return;
			}
			Thread.sleep(25L);
		}
		throw new AssertionError("Room status did not become " + expected);
	}

	private static final class RecordingHandler extends TextWebSocketHandler {

		private final LinkedBlockingQueue<String> payloads =
			new LinkedBlockingQueue<>();
		private final CountDownLatch closed = new CountDownLatch(1);
		private final AtomicReference<CloseStatus> closeStatus =
			new AtomicReference<>();

		@Override
		protected void handleTextMessage(
			WebSocketSession session,
			TextMessage message
		) {
			payloads.add(message.getPayload());
		}

		@Override
		public void afterConnectionClosed(
			WebSocketSession session,
			CloseStatus status
		) {
			closeStatus.set(status);
			closed.countDown();
		}

		private String await(String expected) throws Exception {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (System.nanoTime() < deadline) {
				String payload = payloads.poll(100, TimeUnit.MILLISECONDS);
				if (payload != null && payload.contains(expected)) {
					return payload;
				}
			}
			throw new AssertionError("WebSocket payload not received: " + expected);
		}
	}
}
