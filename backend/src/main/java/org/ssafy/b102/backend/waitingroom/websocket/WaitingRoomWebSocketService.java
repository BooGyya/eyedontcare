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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.ErrorCode;
import org.ssafy.b102.backend.global.openvidu.LiveKitTokenService;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.global.security.SecurityErrorCode;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.waitingroom.entity.RoomStatus;
import org.ssafy.b102.backend.waitingroom.entity.RoomType;
import org.ssafy.b102.backend.waitingroom.entity.WaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.exception.WaitingRoomErrorCode;
import org.ssafy.b102.backend.waitingroom.repository.LeaveWaitingRoomResult;
import org.ssafy.b102.backend.waitingroom.repository.RandomRoomLeaveResult;
import org.ssafy.b102.backend.waitingroom.repository.WaitingRoomSnapshot;
import org.ssafy.b102.backend.waitingroom.service.RandomRematchRequester;
import org.ssafy.b102.backend.waitingroom.service.RandomRematchRequestResult;
import org.ssafy.b102.backend.waitingroom.service.WaitingRoomCommandService;
import org.ssafy.b102.backend.waitingroom.service.WaitingRoomCommandService.StartCommandResult;
import org.ssafy.b102.backend.waitingroom.service.WaitingRoomCommandService.ReadyCommandResult;
import org.ssafy.b102.backend.waitingroom.service.RandomRoomLifecyclePort;
import org.ssafy.b102.backend.waitingroom.service.WaitingRoomService;
import org.ssafy.b102.backend.waitingroom.service.WaitingRoomLeaveOutcome;
import org.ssafy.b102.backend.waitingroom.support.ResolvedWaitingRoomParticipant;
import org.ssafy.b102.backend.waitingroom.support.WaitingRoomParticipantResolver;
import tools.jackson.databind.json.JsonMapper;

@Service
public class WaitingRoomWebSocketService {

	private static final Logger log =
		LoggerFactory.getLogger(WaitingRoomWebSocketService.class);
	private static final String AUTH_TYPE = "AUTH";

	private final WaitingRoomService waitingRoomService;
	private final WaitingRoomCommandService commandService;
	private final WaitingRoomParticipantResolver participantResolver;
	private final WaitingRoomWebSocketSessionRegistry registry;
	private final WaitingRoomCountdownCoordinator countdownCoordinator;
	private final JwtTokenProvider jwtTokenProvider;
	private final LiveKitTokenService liveKitTokenService;
	private final JsonMapper jsonMapper;
	private final WaitingRoomWebSocketProperties properties;
	private final TaskScheduler taskScheduler;
	private final Clock clock;
	private final ObjectProvider<RandomRoomLifecyclePort> lifecyclePortProvider;
	private final ObjectProvider<RandomRematchRequester> rematchRequesterProvider;
	private final ConcurrentMap<String, PendingAuthentication> pending =
		new ConcurrentHashMap<>();

	@Autowired
	public WaitingRoomWebSocketService(
		WaitingRoomService waitingRoomService,
		WaitingRoomCommandService commandService,
		WaitingRoomParticipantResolver participantResolver,
		WaitingRoomWebSocketSessionRegistry registry,
		WaitingRoomCountdownCoordinator countdownCoordinator,
		JwtTokenProvider jwtTokenProvider,
		LiveKitTokenService liveKitTokenService,
		JsonMapper jsonMapper,
		WaitingRoomWebSocketProperties properties,
		@Qualifier("waitingRoomWebSocketTaskScheduler")
		TaskScheduler taskScheduler,
		ObjectProvider<RandomRoomLifecyclePort> lifecyclePortProvider,
		ObjectProvider<RandomRematchRequester> rematchRequesterProvider
	) {
		this(
			waitingRoomService,
			commandService,
			participantResolver,
			registry,
			countdownCoordinator,
			jwtTokenProvider,
			liveKitTokenService,
			jsonMapper,
			properties,
			taskScheduler,
			Clock.systemUTC(),
			lifecyclePortProvider,
			rematchRequesterProvider
		);
	}

	WaitingRoomWebSocketService(
		WaitingRoomService waitingRoomService,
		WaitingRoomCommandService commandService,
		WaitingRoomParticipantResolver participantResolver,
		WaitingRoomWebSocketSessionRegistry registry,
		WaitingRoomCountdownCoordinator countdownCoordinator,
		JwtTokenProvider jwtTokenProvider,
		JsonMapper jsonMapper,
		WaitingRoomWebSocketProperties properties,
		TaskScheduler taskScheduler,
		Clock clock
	) {
		this(
			waitingRoomService,
			commandService,
			participantResolver,
			registry,
			countdownCoordinator,
			jwtTokenProvider,
			null,
			jsonMapper,
			properties,
			taskScheduler,
			clock,
			null,
			null
		);
	}

	WaitingRoomWebSocketService(
		WaitingRoomService waitingRoomService,
		WaitingRoomCommandService commandService,
		WaitingRoomParticipantResolver participantResolver,
		WaitingRoomWebSocketSessionRegistry registry,
		WaitingRoomCountdownCoordinator countdownCoordinator,
		JwtTokenProvider jwtTokenProvider,
		LiveKitTokenService liveKitTokenService,
		JsonMapper jsonMapper,
		WaitingRoomWebSocketProperties properties,
		TaskScheduler taskScheduler,
		Clock clock,
		ObjectProvider<RandomRoomLifecyclePort> lifecyclePortProvider,
		ObjectProvider<RandomRematchRequester> rematchRequesterProvider
	) {
		this.waitingRoomService = waitingRoomService;
		this.commandService = commandService;
		this.participantResolver = participantResolver;
		this.registry = registry;
		this.countdownCoordinator = countdownCoordinator;
		this.jwtTokenProvider = jwtTokenProvider;
		this.liveKitTokenService = liveKitTokenService;
		this.jsonMapper = jsonMapper;
		this.properties = properties;
		this.taskScheduler = taskScheduler;
		this.clock = clock;
		this.lifecyclePortProvider = lifecyclePortProvider;
		this.rematchRequesterProvider = rematchRequesterProvider;
	}

	public void leaveFromRest(
		UUID roomId,
		AuthenticatedUser member,
		UUID guestSessionId
	) {
		handleLeaveOutcome(
			waitingRoomService.leave(roomId, member, guestSessionId)
		);
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
		if (authentication != null) {
			handleAuthentication(authentication, payload);
			return;
		}
		Optional<WaitingRoomConnectionContext> context =
			registry.findBySessionId(session.getId());
		if (context.isEmpty()) {
			reject(session, WaitingRoomErrorCode.INVALID_WEBSOCKET_MESSAGE);
			return;
		}
		handleCommand(context.get(), payload);
	}

	private void handleAuthentication(
		PendingAuthentication authentication,
		String payload
	) {
		if (!authentication.claim()) {
			return;
		}
		pending.remove(authentication.session().getId(), authentication);
		authentication.cancelTimeout();

		try {
			authenticateAndRegister(authentication, payload);
		} catch (BusinessException exception) {
			reject(authentication.session(), exception.getErrorCode());
		} catch (RuntimeException exception) {
			log.error(
				"대기방 WebSocket 인증 처리에 실패했습니다. roomId={}",
				authentication.roomId(),
				exception
			);
			reject(
				authentication.session(),
				WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE
			);
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
		WaitingRoomSnapshot knownSnapshot = null;
		WaitingRoomConnectionContext context = null;
		try {
			knownSnapshot = waitingRoomService.findSnapshot(authentication.roomId());
			WaitingRoomParticipant participant =
				validateParticipant(knownSnapshot, identity.participantKey());

			WebSocketSession safeSession = new ConcurrentWebSocketSessionDecorator(
				authentication.session(),
				Math.toIntExact(properties.sendTimeLimit().toMillis()),
				properties.bufferSizeLimit()
			);
			context = new WaitingRoomConnectionContext(
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

			WaitingRoomSnapshot verified =
				waitingRoomService.findSnapshot(authentication.roomId());
			knownSnapshot = verified;
			validateParticipant(verified, identity.participantKey());
			sendRoomState(context.session(), verified);
			if (verified.room().roomType() == RoomType.RANDOM) {
				markParticipantEntered(
					verified.room().roomId(),
					identity.participantKey()
				);
			}
		} catch (BusinessException exception) {
			if (context != null) {
				registry.unregister(context.sessionId());
			}
			if (shouldCleanupFailedParticipant(exception.getErrorCode(), knownSnapshot)) {
				cleanupFailedParticipant(
					authentication.roomId(),
					identity.participantKey()
				);
			}
			throw exception;
		} catch (RuntimeException exception) {
			if (context == null) {
				throw exception;
			}
			registry.unregister(context.sessionId());
			close(context.session(), CloseStatus.SERVER_ERROR);
			leaveAndBroadcast(context);
		}
	}

	private boolean shouldCleanupFailedParticipant(
		ErrorCode errorCode,
		WaitingRoomSnapshot snapshot
	) {
		if (snapshot != null && snapshot.room().roomType() != RoomType.RANDOM) {
			return false;
		}
		return errorCode == WaitingRoomErrorCode.WAITING_ROOM_NOT_FOUND
			|| errorCode == WaitingRoomErrorCode.WAITING_ROOM_NOT_JOINABLE
			|| errorCode == WaitingRoomErrorCode.PARTICIPANT_NOT_FOUND;
	}

	private void cleanupFailedParticipant(UUID roomId, String participantKey) {
		RandomRoomLifecyclePort port = lifecyclePort();
		if (port == null) {
			return;
		}
		try {
			port.cleanupFailedParticipant(roomId, participantKey);
		} catch (RuntimeException exception) {
			log.warn(
				"랜덤 대기방 입장 실패 cleanup에 실패했습니다. roomId={}",
				roomId
			);
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
		if (snapshot.room().roomStatus() == RoomStatus.IN_GAME) {
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
			WaitingRoomLeaveOutcome outcome =
				waitingRoomService.leaveWithOutcomeByParticipantKey(
					context.roomId(),
					context.participantKey()
				);
			if (outcome == null) {
				handleLegacyInviteLeave(context);
				return;
			}
			if (outcome.roomType() == RoomType.INVITE) {
				LeaveWaitingRoomResult result = outcome.inviteResult();
				if (result == LeaveWaitingRoomResult.ROOM_CLOSED) {
					countdownCoordinator.cancel(context.roomId());
				}
				if (result != LeaveWaitingRoomResult.ALREADY_CLOSED) {
					broadcastLatestState(context.roomId());
				}
			} else {
				handleLeaveOutcome(outcome);
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

	private void handleLegacyInviteLeave(
		WaitingRoomConnectionContext context
	) {
		LeaveWaitingRoomResult result =
			waitingRoomService.leaveByParticipantKey(
				context.roomId(),
				context.participantKey()
			);
		if (result == LeaveWaitingRoomResult.ROOM_CLOSED) {
			countdownCoordinator.cancel(context.roomId());
		}
		if (result != LeaveWaitingRoomResult.ALREADY_CLOSED) {
			broadcastLatestState(context.roomId());
		}
	}

	private void handleLeaveOutcome(WaitingRoomLeaveOutcome outcome) {
		if (outcome.roomType() == RoomType.INVITE) {
			return;
		}
		RandomRoomLeaveResult result = outcome.randomResult();
		if (result.status() == RandomRoomLeaveResult.Status.ALREADY_CLOSED) {
			return;
		}
		if (result.status() != RandomRoomLeaveResult.Status.CLOSED_NOW) {
			return;
		}
		countdownCoordinator.cancel(result.roomId());
		broadcastRandomClosed(result.roomId());
		requestRematchIfRemainingSessionAlive(result);
		closeRoomSessions(result.roomId());
	}

	private void broadcastRandomClosed(UUID roomId) {
		WaitingRoomSnapshot snapshot = waitingRoomService.findSnapshot(roomId);
		for (WaitingRoomConnectionContext context : registry.findByRoomId(roomId)) {
			try {
				sendRoomState(context.session(), snapshot);
			} catch (RuntimeException exception) {
				log.warn("랜덤 대기방 CLOSED 상태 전송에 실패했습니다. roomId={}", roomId);
			}
		}
	}

	private void requestRematchIfRemainingSessionAlive(
		RandomRoomLeaveResult leaveResult
	) {
		Optional<WaitingRoomConnectionContext> remaining =
			registry.findByRoomAndParticipant(
				leaveResult.roomId(),
				leaveResult.remainingParticipantKey()
			);
		if (
			remaining.isEmpty() ||
			!remaining.get().session().isOpen() ||
			remaining.get().isLeaveSuppressed()
		) {
			return;
		}
		RandomRematchRequester requester = rematchRequester();
		if (requester == null) {
			log.warn(
				"랜덤 재매칭 adapter가 없습니다. roomId={}, action=requeue",
				leaveResult.roomId()
			);
			return;
		}
		try {
			RandomRematchRequestResult result = requester.requeueRemaining(
				leaveResult.roomId(),
				leaveResult.gameName(),
				leaveResult.remainingParticipantKey()
			);
			if (result == RandomRematchRequestResult.FAILED) {
				log.warn("랜덤 자동 재매칭 요청에 실패했습니다. roomId={}", leaveResult.roomId());
			}
		} catch (RuntimeException exception) {
			log.warn("랜덤 자동 재매칭 adapter 호출에 실패했습니다. roomId={}", leaveResult.roomId());
		}
	}

	private RandomRematchRequester rematchRequester() {
		return rematchRequesterProvider == null
			? null
			: rematchRequesterProvider.getIfAvailable();
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

	private void handleCommand(
		WaitingRoomConnectionContext context,
		String payload
	) {
		try {
			WaitingRoomCommandTypeFrame typeFrame =
				jsonMapper.readValue(payload, WaitingRoomCommandTypeFrame.class);
			if (typeFrame == null || !hasText(typeFrame.getType())) {
				throw invalidMessage();
			}
			switch (typeFrame.getType()) {
				case "CALIBRATION_STATUS" ->
					handleCalibration(context, payload);
				case "READY_STATUS" -> handleReady(context, payload);
				case "START_GAME" -> handleStartGame(context, payload);
				default -> throw invalidMessage();
			}
		} catch (BusinessException exception) {
			if (
				exception.getErrorCode() ==
				WaitingRoomErrorCode.INVALID_WEBSOCKET_MESSAGE
			) {
				reject(context.session(), exception.getErrorCode());
			} else if (exception.getErrorCode().status().is5xxServerError()) {
				reject(context.session(), exception.getErrorCode());
			} else {
				sendError(context.session(), exception.getErrorCode());
			}
		} catch (RuntimeException exception) {
			reject(
				context.session(),
				WaitingRoomErrorCode.INVALID_WEBSOCKET_MESSAGE
			);
		}
	}

	private void handleCalibration(
		WaitingRoomConnectionContext context,
		String payload
	) {
		CalibrationStatusFrame frame = readFrame(
			payload,
			CalibrationStatusFrame.class
		);
		if (
			frame.hasUnknownFields() ||
			!"CALIBRATION_STATUS".equals(frame.getType()) ||
			frame.getCalibrationStatus() == null
		) {
			throw invalidMessage();
		}
		boolean changed = commandService.updateCalibration(
			context.roomId(),
			context.participantKey(),
			frame.getCalibrationStatus()
		);
		if (changed) {
			broadcastLatestState(context.roomId());
		}
	}

	private void handleReady(
		WaitingRoomConnectionContext context,
		String payload
	) {
		ReadyStatusFrame frame = readFrame(payload, ReadyStatusFrame.class);
		if (
			frame.hasUnknownFields() ||
			!"READY_STATUS".equals(frame.getType()) ||
			frame.getIsReady() == null
		) {
			throw invalidMessage();
		}
		WaitingRoomSnapshot snapshot = waitingRoomService.findSnapshot(context.roomId());
		if (snapshot.room().roomType() == RoomType.INVITE) {
			if (
				commandService.updateReady(
					context.roomId(),
					context.participantKey(),
					frame.getIsReady()
				)
			) {
				broadcastLatestState(context.roomId());
			}
			return;
		}
		ReadyCommandResult readyResult =
			commandService.updateReadyAndStartRandom(
				context.roomId(),
				context.participantKey(),
				frame.getIsReady()
			);
		if (readyResult.countdown()) {
			StartCommandResult startResult = new StartCommandResult(
				readyResult.changed(),
				readyResult.roomCode(),
				readyResult.countdownId(),
				readyResult.countdownEndsAt()
			);
			boolean scheduled;
			try {
				scheduled = countdownCoordinator.scheduleIfAbsent(
					context.roomId(),
					startResult.countdownEndsAt(),
					() -> completeCountdown(context.roomId(), startResult)
				);
			} catch (RuntimeException exception) {
				handleScheduleFailure(context, startResult, exception);
				return;
			}
			if (readyResult.changed() && scheduled) {
				broadcastLatestState(context.roomId());
			}
		} else if (readyResult.changed()) {
			broadcastLatestState(context.roomId());
		}
	}

	private void handleStartGame(
		WaitingRoomConnectionContext context,
		String payload
	) {
		StartGameFrame frame = readFrame(payload, StartGameFrame.class);
		if (
			frame.hasUnknownFields() ||
			!"START_GAME".equals(frame.getType())
		) {
			throw invalidMessage();
		}
		StartCommandResult result = commandService.startGame(
			context.roomId(),
			context.participantKey()
		);
		boolean scheduled;
		try {
			scheduled = countdownCoordinator.scheduleIfAbsent(
				context.roomId(),
				result.countdownEndsAt(),
				() -> completeCountdown(context.roomId(), result)
			);
		} catch (RuntimeException exception) {
			handleScheduleFailure(context, result, exception);
			return;
		}
		if (result.started() && scheduled) {
			broadcastLatestState(context.roomId());
		}
	}

	private void handleScheduleFailure(
		WaitingRoomConnectionContext context,
		StartCommandResult result,
		RuntimeException cause
	) {
		log.error(
			"대기방 countdown task 등록에 실패했습니다. roomId={}",
			context.roomId(),
			cause
		);
		try {
			if (
				commandService.rollbackCountdown(
					context.roomId(),
					result.roomCode(),
					result.countdownId()
				)
			) {
				sendError(
					context.session(),
					WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE
				);
				broadcastLatestState(context.roomId());
				return;
			}
		} catch (RuntimeException exception) {
			log.error(
				"대기방 countdown rollback에 실패했습니다. roomId={}",
				context.roomId(),
				exception
			);
		}
		closeRoomWithInternalError(context.roomId());
	}

	private void completeCountdown(
		UUID roomId,
		StartCommandResult result
	) {
		try {
			if (
				!commandService.completeCountdown(
					roomId,
					result.roomCode(),
					result.countdownId(),
					result.countdownEndsAt()
				)
			) {
				return;
			}
			WaitingRoomSnapshot snapshot = waitingRoomService.findSnapshot(roomId);
			if (snapshot.room().roomType() == RoomType.RANDOM) {
				completeRandomRoom(snapshot);
			}
			sendGameStartAndClose(snapshot);
		} catch (RuntimeException exception) {
			log.error(
				"대기방 countdown 완료 처리에 실패했습니다. roomId={}",
				roomId,
				exception
			);
			closeRoomWithInternalError(roomId);
		}
	}

	private void markParticipantEntered(UUID roomId, String participantKey) {
		RandomRoomLifecyclePort port = lifecyclePort();
		if (port == null) {
			log.warn("랜덤 대기방 lifecycle adapter가 없습니다. roomId={}, action=enter", roomId);
			return;
		}
		try {
			port.markParticipantEntered(roomId, participantKey);
		} catch (RuntimeException exception) {
			log.warn("랜덤 대기방 입장 lifecycle 반영에 실패했습니다. roomId={}", roomId);
		}
	}

	private void completeRandomRoom(WaitingRoomSnapshot snapshot) {
		RandomRoomLifecyclePort port = lifecyclePort();
		if (port == null) {
			log.warn(
				"랜덤 대기방 lifecycle adapter가 없습니다. roomId={}, action=complete",
				snapshot.room().roomId()
			);
			return;
		}
		try {
			port.completeRandomRoom(
				snapshot.room().roomId(),
				snapshot.participants().stream()
					.sorted(java.util.Comparator.comparingInt(
						participant -> participant.slotNo()
					))
					.map(participant -> participant.participantKey())
					.toList()
			);
		} catch (RuntimeException exception) {
			log.warn(
				"랜덤 대기방 완료 lifecycle 반영에 실패했습니다. roomId={}",
				snapshot.room().roomId()
			);
		}
	}

	private RandomRoomLifecyclePort lifecyclePort() {
		return lifecyclePortProvider == null
			? null
			: lifecyclePortProvider.getIfAvailable();
	}

	private void sendGameStartAndClose(WaitingRoomSnapshot snapshot) {
		UUID roomId = snapshot.room().roomId();
		String mediaUrl = liveKitTokenService == null ? null : liveKitTokenService.url();
		for (
			WaitingRoomConnectionContext context :
				registry.findByRoomId(roomId)
		) {
			try {
				send(context.session(), gameStartEvent(snapshot, context, mediaUrl));
			} catch (RuntimeException exception) {
				log.warn("대기방 GAME_START 전송에 실패했습니다. roomId={}", roomId);
			} finally {
				registry.markSuppressLeave(context.sessionId());
				registry.unregister(context.sessionId());
				close(context.session(), CloseStatus.NORMAL);
			}
		}
	}

	/**
	 * 참가자별 GAME_START 이벤트를 만든다. WebRTC 미디어 토큰은 참가자 identity마다 달라야 하므로
	 * 수신자별로 새로 발급한다. 미디어 서비스가 없는 테스트 경로에서는 토큰 없이 이벤트만 만든다.
	 */
	private WaitingRoomWebSocketEvent<WaitingRoomGameStart> gameStartEvent(
		WaitingRoomSnapshot snapshot,
		WaitingRoomConnectionContext context,
		String mediaUrl
	) {
		String token = liveKitTokenService == null
			? null
			: liveKitTokenService.issueToken(
				context.participantKey(),
				resolveDisplayName(snapshot, context.participantKey()),
				snapshot.room().roomId().toString()
			);
		return WaitingRoomWebSocketEvent.gameStart(
			new WaitingRoomGameStart(
				snapshot.room().roomId(),
				snapshot.room().gameName(),
				clock.instant(),
				mediaUrl,
				token
			)
		);
	}

	private String resolveDisplayName(
		WaitingRoomSnapshot snapshot,
		String participantKey
	) {
		return snapshot.participants().stream()
			.filter(participant -> participant.participantKey().equals(participantKey))
			.map(WaitingRoomParticipant::displayName)
			.findFirst()
			.orElse(participantKey);
	}

	private void closeRoomWithInternalError(UUID roomId) {
		for (WaitingRoomConnectionContext context : registry.findByRoomId(roomId)) {
			try {
				sendError(
					context.session(),
					WaitingRoomErrorCode.WAITING_ROOM_STORE_UNAVAILABLE
				);
			} catch (RuntimeException ignored) {
				// 전체 room 정리를 계속한다.
			}
			registry.markSuppressLeave(context.sessionId());
			registry.unregister(context.sessionId());
			close(context.session(), CloseStatus.SERVER_ERROR);
		}
	}

	private <T> T readFrame(String payload, Class<T> type) {
		try {
			return jsonMapper.readValue(payload, type);
		} catch (RuntimeException exception) {
			throw invalidMessage();
		}
	}

	private void sendError(WebSocketSession session, ErrorCode errorCode) {
		send(
			session,
			WaitingRoomWebSocketEvent.error(
				WaitingRoomWebSocketError.from(errorCode)
			)
		);
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
