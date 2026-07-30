package org.ssafy.b102.backend.waitingroom.websocket;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.ErrorCode;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.global.security.SecurityErrorCode;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
import org.ssafy.b102.backend.waitingroom.repository.LeaveWaitingRoomResult;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomSnapshot;
import org.ssafy.b102.backend.waitingroom.service.WaitingRoomService;
import org.ssafy.b102.backend.waitingroom.support.ResolvedWaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.support.WaitingRoomParticipantResolver;
import tools.jackson.databind.json.JsonMapper;

@Service
public class WaitingRoomWebSocketService {

	private static final Logger log =
		LoggerFactory.getLogger(WaitingRoomWebSocketService.class);
	private static final String AUTH_TYPE = "AUTH";

	private final WaitingRoomService waitingRoomService;
	private final WaitingRoomParticipantResolver participantResolver;
	private final WaitingRoomWebSocketSessionRegistry registry;
	private final JwtTokenProvider jwtTokenProvider;
	private final JsonMapper jsonMapper;
	private final WaitingRoomWebSocketProperties properties;
	private final TaskScheduler taskScheduler;
	private final Clock clock;
	private final ConcurrentMap<String, PendingAuthentication> pending =
		new ConcurrentHashMap<>();

	@Autowired
	public WaitingRoomWebSocketService(
		WaitingRoomService waitingRoomService,
		WaitingRoomParticipantResolver participantResolver,
		WaitingRoomWebSocketSessionRegistry registry,
		JwtTokenProvider jwtTokenProvider,
		JsonMapper jsonMapper,
		WaitingRoomWebSocketProperties properties,
		@Qualifier("waitingRoomWebSocketTaskScheduler")
		TaskScheduler taskScheduler
	) {
		this(
			waitingRoomService,
			participantResolver,
			registry,
			jwtTokenProvider,
			jsonMapper,
			properties,
			taskScheduler,
			Clock.systemUTC()
		);
	}

	WaitingRoomWebSocketService(
		WaitingRoomService waitingRoomService,
		WaitingRoomParticipantResolver participantResolver,
		WaitingRoomWebSocketSessionRegistry registry,
		JwtTokenProvider jwtTokenProvider,
		JsonMapper jsonMapper,
		WaitingRoomWebSocketProperties properties,
		TaskScheduler taskScheduler,
		Clock clock
	) {
		this.waitingRoomService = waitingRoomService;
		this.participantResolver = participantResolver;
		this.registry = registry;
		this.jwtTokenProvider = jwtTokenProvider;
		this.jsonMapper = jsonMapper;
		this.properties = properties;
		this.taskScheduler = taskScheduler;
		this.clock = clock;
	}

	public void connectionEstablished(
		WebSocketSession session,
		UUID roomId
	) {
		PendingAuthentication authentication =
			new PendingAuthentication(session, roomId);
		if (pending.putIfAbsent(session.getId(), authentication) != null) {
			reject(session, WaitingRoomErrorCode.INVALID_WEBSOCKET_MESSAGE);
			return;
		}
		ScheduledFuture<?> timeout = taskScheduler.schedule(
			() -> authenticationTimedOut(session.getId()),
			clock.instant().plus(properties.authTimeout())
		);
		authentication.setTimeout(timeout);
	}

	public void invalidPath(WebSocketSession session) {
		reject(session, WaitingRoomErrorCode.INVALID_WEBSOCKET_MESSAGE);
	}

	public void handleMessage(WebSocketSession session, String payload) {
		PendingAuthentication authentication = pending.get(session.getId());
		if (authentication == null) {
			reject(session, WaitingRoomErrorCode.INVALID_WEBSOCKET_MESSAGE);
			return;
		}
		if (!authentication.claim()) {
			return;
		}
		pending.remove(session.getId(), authentication);
		authentication.cancelTimeout();

		try {
			authenticateAndRegister(authentication, payload);
		} catch (BusinessException exception) {
			reject(session, exception.getErrorCode());
		} catch (RuntimeException exception) {
			log.error(
				"대기방 WebSocket 인증 처리에 실패했습니다. roomId={}",
				authentication.roomId(),
				exception
			);
			reject(session, WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE);
		}
	}

	public void connectionClosed(WebSocketSession session) {
		cancelPending(session.getId());
		registry.unregister(session.getId()).ifPresent(this::leaveAndBroadcast);
	}

	public void transportError(WebSocketSession session) {
		connectionClosed(session);
		close(session, CloseStatus.SERVER_ERROR);
	}

	private void authenticateAndRegister(
		PendingAuthentication authentication,
		String payload
	) {
		WaitingRoomAuthFrame frame = parseAuthFrame(payload);
		ResolvedWaitingRoomParticipant identity = resolveIdentity(frame);
		WaitingRoomSnapshot snapshot =
			waitingRoomService.findSnapshot(authentication.roomId());
		WaitingRoomParticipant participant =
			validateParticipant(snapshot, identity.participantKey());

		WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
			authentication.session(),
			Math.toIntExact(properties.sendTimeLimit().toMillis()),
			properties.bufferSizeLimit()
		);
		WaitingRoomConnectionContext context =
			new WaitingRoomConnectionContext(
				authentication.session().getId(),
				authentication.roomId(),
				identity.participantKey(),
				participant.roomRole(),
				clock.instant(),
				safeSession
			);
		if (!registry.registerIfAbsent(context)) {
			throw new BusinessException(
				WaitingRoomErrorCode.WEBSOCKET_ALREADY_CONNECTED
			);
		}

		try {
			WaitingRoomSnapshot verified =
				waitingRoomService.findSnapshot(authentication.roomId());
			validateParticipant(verified, identity.participantKey());
			sendRoomState(context.session(), verified);
		} catch (BusinessException exception) {
			registry.unregister(context.sessionId());
			throw exception;
		} catch (RuntimeException exception) {
			registry.unregister(context.sessionId());
			close(context.session(), CloseStatus.SERVER_ERROR);
			leaveAndBroadcast(context);
		}
	}

	private WaitingRoomAuthFrame parseAuthFrame(String payload) {
		try {
			WaitingRoomAuthFrame frame =
				jsonMapper.readValue(payload, WaitingRoomAuthFrame.class);
			if (
				frame == null ||
				frame.hasUnknownFields() ||
				!AUTH_TYPE.equals(frame.getType())
			) {
				throw invalidMessage();
			}
			return frame;
		} catch (BusinessException exception) {
			throw exception;
		} catch (RuntimeException exception) {
			throw invalidMessage();
		}
	}

	private ResolvedWaitingRoomParticipant resolveIdentity(
		WaitingRoomAuthFrame frame
	) {
		if (hasText(frame.getAccessToken())) {
			Long userId = jwtTokenProvider
				.parseAccessTokenUserId(frame.getAccessToken())
				.orElseThrow(() ->
					new BusinessException(SecurityErrorCode.INVALID_ACCESS_TOKEN));
			try {
				return participantResolver.resolveExisting(
					new AuthenticatedUser(userId),
					null
				);
			} catch (BusinessException exception) {
				throw new BusinessException(SecurityErrorCode.INVALID_ACCESS_TOKEN);
			}
		}
		if (!hasText(frame.getGuestSessionId())) {
			throw invalidMessage();
		}
		try {
			return participantResolver.resolveExisting(
				null,
				UUID.fromString(frame.getGuestSessionId())
			);
		} catch (IllegalArgumentException exception) {
			throw invalidMessage();
		}
	}

	private WaitingRoomParticipant validateParticipant(
		WaitingRoomSnapshot snapshot,
		String participantKey
	) {
		if (snapshot.room().roomStatus() == RoomStatus.CLOSED) {
			throw new BusinessException(
				WaitingRoomErrorCode.WAITING_ROOM_NOT_JOINABLE
			);
		}
		if (
			snapshot.room().roomStatus() != RoomStatus.WAITING &&
			snapshot.room().roomStatus() != RoomStatus.COUNTDOWN
		) {
			throw new BusinessException(
				WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE
			);
		}
		return snapshot.participants().stream()
			.filter(participant ->
				participant.participantKey().equals(participantKey))
			.findFirst()
			.orElseThrow(() ->
				new BusinessException(WaitingRoomErrorCode.PARTICIPANT_NOT_FOUND));
	}

	private void authenticationTimedOut(String sessionId) {
		PendingAuthentication authentication = pending.get(sessionId);
		if (authentication == null || !authentication.claim()) {
			return;
		}
		pending.remove(sessionId, authentication);
		reject(
			authentication.session(),
			WaitingRoomErrorCode.WEBSOCKET_AUTH_TIMEOUT
		);
	}

	private void cancelPending(String sessionId) {
		PendingAuthentication authentication = pending.remove(sessionId);
		if (authentication != null) {
			authentication.cancelTimeout();
		}
	}

	private void leaveAndBroadcast(WaitingRoomConnectionContext context) {
		if (context.isLeaveSuppressed()) {
			return;
		}
		try {
			LeaveWaitingRoomResult result =
				waitingRoomService.leaveByParticipantKey(
					context.roomId(),
					context.participantKey()
				);
			if (result != LeaveWaitingRoomResult.ALREADY_CLOSED) {
				broadcastLatestState(context.roomId());
			}
		} catch (BusinessException exception) {
			if (
				exception.getErrorCode() ==
				WaitingRoomErrorCode.PARTICIPANT_NOT_FOUND
			) {
				broadcastLatestState(context.roomId());
				return;
			}
			log.warn(
				"대기방 WebSocket 종료 정리에 실패했습니다. roomId={}, code={}",
				context.roomId(),
				exception.getErrorCode().code()
			);
		} catch (RuntimeException exception) {
			log.error(
				"대기방 WebSocket 종료 정리에 실패했습니다. roomId={}",
				context.roomId(),
				exception
			);
		}
	}

	private void broadcastLatestState(UUID roomId) {
		final WaitingRoomSnapshot snapshot;
		try {
			snapshot = waitingRoomService.findSnapshot(roomId);
		} catch (BusinessException exception) {
			log.warn(
				"대기방 WebSocket 상태 조회에 실패했습니다. roomId={}, code={}",
				roomId,
				exception.getErrorCode().code()
			);
			return;
		}

		for (WaitingRoomConnectionContext context : registry.findByRoomId(roomId)) {
			try {
				sendRoomState(context.session(), snapshot);
			} catch (RuntimeException exception) {
				registry.unregister(context.sessionId())
					.ifPresent(this::leaveAndBroadcast);
				close(context.session(), CloseStatus.SERVER_ERROR);
			}
		}
		if (snapshot.room().roomStatus() == RoomStatus.CLOSED) {
			closeRoomSessions(roomId);
		}
	}

	private void closeRoomSessions(UUID roomId) {
		for (WaitingRoomConnectionContext context : registry.findByRoomId(roomId)) {
			registry.markSuppressLeave(context.sessionId());
			registry.unregister(context.sessionId());
			close(context.session(), CloseStatus.NORMAL);
		}
	}

	private void sendRoomState(
		WebSocketSession session,
		WaitingRoomSnapshot snapshot
	) {
		send(
			session,
			WaitingRoomWebSocketEvent.roomState(
				WaitingRoomRoomState.from(snapshot)
			)
		);
	}

	private void reject(WebSocketSession session, ErrorCode errorCode) {
		try {
			send(
				session,
				WaitingRoomWebSocketEvent.error(
					WaitingRoomWebSocketError.from(errorCode)
				)
			);
		} catch (RuntimeException ignored) {
			// 오류 frame 전송 실패 여부와 관계없이 연결은 종료한다.
		}
		CloseStatus closeStatus = errorCode.status().is5xxServerError()
			? CloseStatus.SERVER_ERROR
			: CloseStatus.POLICY_VIOLATION;
		close(session, closeStatus);
	}

	private void send(WebSocketSession session, Object event) {
		try {
			session.sendMessage(
				new TextMessage(jsonMapper.writeValueAsString(event))
			);
		} catch (IOException | RuntimeException exception) {
			throw new IllegalStateException("WebSocket message send failed", exception);
		}
	}

	private void close(WebSocketSession session, CloseStatus status) {
		try {
			if (session.isOpen()) {
				session.close(status);
			}
		} catch (IOException exception) {
			log.debug("대기방 WebSocket 연결 종료에 실패했습니다.", exception);
		}
	}

	private static BusinessException invalidMessage() {
		return new BusinessException(
			WaitingRoomErrorCode.INVALID_WEBSOCKET_MESSAGE
		);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private static final class PendingAuthentication {

		private final WebSocketSession session;
		private final UUID roomId;
		private final AtomicBoolean claimed = new AtomicBoolean(false);
		private volatile ScheduledFuture<?> timeout;

		private PendingAuthentication(WebSocketSession session, UUID roomId) {
			this.session = session;
			this.roomId = roomId;
		}

		private WebSocketSession session() {
			return session;
		}

		private UUID roomId() {
			return roomId;
		}

		private boolean claim() {
			return claimed.compareAndSet(false, true);
		}

		private void setTimeout(ScheduledFuture<?> timeout) {
			this.timeout = timeout;
			if (claimed.get()) {
				cancelTimeout();
			}
		}

		private void cancelTimeout() {
			ScheduledFuture<?> current = timeout;
			if (current != null) {
				current.cancel(false);
			}
		}
	}
}
