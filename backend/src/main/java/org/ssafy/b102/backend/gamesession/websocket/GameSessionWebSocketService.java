package org.ssafy.b102.backend.gamesession.websocket;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.guest.support.GuestParticipantKey;
import tools.jackson.databind.json.JsonMapper;

/**
 * 게임 플레이 중 같은 방(roomId)에 있는 두 참가자 사이의 실시간 이벤트를 중계한다.
 *
 * <p>대기방 WebSocket은 GAME_START 직후 끊기므로, 게임 화면은 {@code /ws/game-sessions/{roomId}}로
 * 따로 접속한다. 익명으로 연결을 맺고 첫 프레임({@code AUTH})으로 인증한 뒤, 이후 {@code PLAYER_EVENT}를
 * 같은 방의 상대에게 그대로 중계한다. 서버는 이벤트 내용을 해석·검증하지 않는다(최종 점수/승패는 각
 * 클라이언트의 게임 로직과 결과 저장이 담당). 인증 성공 시 {@code SESSION_STATE}로 알린다.
 */
@Service
public class GameSessionWebSocketService {

	private static final Logger log =
		LoggerFactory.getLogger(GameSessionWebSocketService.class);

	private static final String MEMBER_KEY_PREFIX = "USER:";
	private static final String ATTR_ROOM_ID = "gamesession.roomId";
	private static final String ATTR_PARTICIPANT_KEY = "gamesession.participantKey";

	private final JwtTokenProvider jwtTokenProvider;
	private final GuestSessionService guestSessionService;
	private final JsonMapper jsonMapper;

	/** roomId → 그 방에 연결된 세션들. 두 명이 접속하며, 동시성 대비로 concurrent 컬렉션을 쓴다. */
	private final Map<UUID, Set<WebSocketSession>> rooms = new ConcurrentHashMap<>();

	public GameSessionWebSocketService(
		JwtTokenProvider jwtTokenProvider,
		GuestSessionService guestSessionService,
		JsonMapper jsonMapper
	) {
		this.jwtTokenProvider = jwtTokenProvider;
		this.guestSessionService = guestSessionService;
		this.jsonMapper = jsonMapper;
	}

	public void connectionEstablished(WebSocketSession session, UUID roomId) {
		if (roomId == null) {
			close(session, CloseStatus.BAD_DATA);
			return;
		}
		session.getAttributes().put(ATTR_ROOM_ID, roomId);
	}

	public void handleMessage(WebSocketSession session, String payload) {
		UUID roomId = (UUID) session.getAttributes().get(ATTR_ROOM_ID);
		if (roomId == null) {
			close(session, CloseStatus.BAD_DATA);
			return;
		}

		String participantKey =
			(String) session.getAttributes().get(ATTR_PARTICIPANT_KEY);
		if (participantKey == null) {
			authenticate(session, roomId, payload);
			return;
		}

		relayPlayerEvent(session, roomId, participantKey, payload);
	}

	public void connectionClosed(WebSocketSession session) {
		UUID roomId = (UUID) session.getAttributes().get(ATTR_ROOM_ID);
		String participantKey =
			(String) session.getAttributes().get(ATTR_PARTICIPANT_KEY);
		if (roomId == null) {
			return;
		}

		Set<WebSocketSession> sessions = rooms.get(roomId);
		if (sessions != null) {
			sessions.remove(session);
			if (sessions.isEmpty()) {
				rooms.remove(roomId);
			} else if (participantKey != null) {
				broadcastToOthers(sessions, session, new OutboundFrame(
					"PARTICIPANT_LEFT",
					Map.of("participantKey", participantKey)
				));
			}
		}
	}

	private void authenticate(WebSocketSession session, UUID roomId, String payload) {
		String participantKey = resolveParticipantKey(payload);
		if (participantKey == null) {
			close(session, CloseStatus.NOT_ACCEPTABLE);
			return;
		}

		session.getAttributes().put(ATTR_PARTICIPANT_KEY, participantKey);
		Set<WebSocketSession> sessions = rooms.computeIfAbsent(
			roomId,
			key -> Collections.newSetFromMap(new ConcurrentHashMap<>())
		);
		sessions.add(session);

		List<String> participantKeys = sessions.stream()
			.map(other -> (String) other.getAttributes().get(ATTR_PARTICIPANT_KEY))
			.filter(key -> key != null)
			.distinct()
			.toList();

		send(session, new OutboundFrame("SESSION_STATE", Map.of(
			"roomId", roomId.toString(),
			"participants", participantKeys.stream()
				.map(key -> Map.of("participantKey", key))
				.toList()
		)));
	}

	private void relayPlayerEvent(
		WebSocketSession session,
		UUID roomId,
		String participantKey,
		String payload
	) {
		PlayerEventFrame frame;
		try {
			frame = jsonMapper.readValue(payload, PlayerEventFrame.class);
		} catch (RuntimeException exception) {
			return;
		}
		if (frame == null || frame.eventType() == null) {
			return;
		}

		Set<WebSocketSession> sessions = rooms.get(roomId);
		if (sessions == null) {
			return;
		}

		OutboundFrame relayed = new OutboundFrame("PLAYER_EVENT", Map.of(
			"participantKey", participantKey,
			"eventType", frame.eventType(),
			"payload", frame.payload() == null ? Map.of() : frame.payload(),
			"occurredAt", Instant.now().toString()
		));
		broadcastToOthers(sessions, session, relayed);
	}

	private void broadcastToOthers(
		Set<WebSocketSession> sessions,
		WebSocketSession sender,
		OutboundFrame frame
	) {
		for (WebSocketSession other : sessions) {
			if (other == sender || !other.isOpen()) {
				continue;
			}
			send(other, frame);
		}
	}

	/**
	 * 인증 프레임에서 참가자 키를 푼다. 회원은 JWT userId → {@code USER:{id}}, 게스트는
	 * 세션 검증 후 {@code GUEST:{uuid}}. 실패하면 null을 반환해 연결을 닫게 한다.
	 */
	private String resolveParticipantKey(String payload) {
		AuthFrame frame;
		try {
			frame = jsonMapper.readValue(payload, AuthFrame.class);
		} catch (RuntimeException exception) {
			return null;
		}
		if (frame == null) {
			return null;
		}
		if (hasText(frame.accessToken())) {
			return jwtTokenProvider.parseAccessTokenUserId(frame.accessToken())
				.map(userId -> MEMBER_KEY_PREFIX + userId)
				.orElse(null);
		}
		if (hasText(frame.guestSessionId())) {
			try {
				UUID sessionId = UUID.fromString(frame.guestSessionId());
				guestSessionService.validate(sessionId);
				return new GuestParticipantKey(sessionId).value();
			} catch (IllegalArgumentException | BusinessException exception) {
				return null;
			}
		}

		return null;
	}

	private void send(WebSocketSession session, OutboundFrame frame) {
		try {
			session.sendMessage(new TextMessage(jsonMapper.writeValueAsString(frame)));
		} catch (IOException | RuntimeException exception) {
			log.warn("게임 세션 메시지 전송에 실패했습니다.", exception);
		}
	}

	private void close(WebSocketSession session, CloseStatus status) {
		try {
			session.close(status);
		} catch (IOException exception) {
			log.debug("세션을 닫는 중 예외가 발생했습니다. 무시합니다.", exception);
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private record OutboundFrame(String type, Object data) {
	}

	private record AuthFrame(String type, String accessToken, String guestSessionId) {
	}

	private record PlayerEventFrame(
		String type,
		String eventType,
		Map<String, Object> payload,
		Long occurredAt
	) {
	}
}
