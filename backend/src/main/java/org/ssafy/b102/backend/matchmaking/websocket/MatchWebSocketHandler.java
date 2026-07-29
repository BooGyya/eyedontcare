package org.ssafy.b102.backend.matchmaking.websocket;

import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.matchmaking.entity.MatchStatus;
import org.ssafy.b102.backend.matchmaking.service.MatchNotifier;
import org.ssafy.b102.backend.matchmaking.service.MatchmakingService;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code /ws/match} 핸들러. 익명으로 연결을 맺고 첫 프레임으로 인증한다.
 *
 * <p>구독 개념 없이 특정 두 명에게 한 번 보내면 끝이므로 STOMP가 아니라 raw WebSocket을 쓴다.
 * STOMP를 쓰면 프레임에 STOMP 헤더가 붙어 명세({@code MATCH_SUCCESS})와 달라진다.
 *
 * <p>연결 순서상 클라이언트는 {@code /api/v1/match/join} 호출 전에 연결해야 푸시를 놓치지 않는다.
 * 늦게 연결해 이미 성사돼 있는 경우를 위해, 인증 직후 상태가 {@code ENTERING_ROOM}이면 즉시
 * {@code MATCH_SUCCESS}를 다시 보낸다. REST 신청 응답도 같은 상태를 담아 fallback이 된다.
 */
@Component
public class MatchWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(MatchWebSocketHandler.class);

	/**
	 * 첫 프레임 인증 이후 세션에 심는 표시. 이후 프레임으로 다른 키를 밀어넣는 세션 탈취를 막는다.
	 */
	private static final String AUTHENTICATED_ATTRIBUTE = "matchmaking.authenticated";

	private final MatchmakingService matchmakingService;
	private final MatchSessionRegistry registry;
	private final MatchNotifier matchNotifier;
	private final JsonMapper jsonMapper;

	public MatchWebSocketHandler(
		MatchmakingService matchmakingService,
		MatchSessionRegistry registry,
		MatchNotifier matchNotifier,
		JsonMapper jsonMapper
	) {
		this.matchmakingService = matchmakingService;
		this.registry = registry;
		this.matchNotifier = matchNotifier;
		this.jsonMapper = jsonMapper;
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		if (Boolean.TRUE.equals(session.getAttributes().get(AUTHENTICATED_ATTRIBUTE))) {
			return;
		}

		String participantKey = parseParticipantKey(message.getPayload());
		if (participantKey == null) {
			close(session, CloseStatus.NOT_ACCEPTABLE);
			return;
		}

		try {
			matchmakingService.validateParticipant(participantKey);
		} catch (BusinessException exception) {
			close(session, CloseStatus.NOT_ACCEPTABLE);
			return;
		}

		session.getAttributes().put(AUTHENTICATED_ATTRIBUTE, Boolean.TRUE);
		registry.register(participantKey, session);
		pushIfAlreadyMatched(participantKey);
	}

	/**
	 * 기능 정의서의 "연결 종료 시 자동 정리"를 구현한다. 조용해야 한다 —
	 * 이미 {@code ENTERING_ROOM}이면 취소하지 않고, 신청이 없어도 예외를 던지지 않는다.
	 */
	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		registry.unregister(session).ifPresent(matchmakingService::cancelSilently);
	}

	private void pushIfAlreadyMatched(String participantKey) {
		matchmakingService.findEntry(participantKey)
			.filter(entry -> entry.matchStatus() == MatchStatus.ENTERING_ROOM && entry.waitingRoomId() != null)
			.ifPresent(entry ->
				matchNotifier.notifyMatched(entry.participantKey(), entry.waitingRoomId(), entry.gameType()));
	}

	private String parseParticipantKey(String payload) {
		try {
			MatchAuthFrame frame = jsonMapper.readValue(payload, MatchAuthFrame.class);
			return frame == null ? null : frame.participantKey();
		} catch (RuntimeException exception) {
			return null;
		}
	}

	private void close(WebSocketSession session, CloseStatus status) {
		try {
			session.close(status);
		} catch (IOException exception) {
			log.debug("세션을 닫는 중 예외가 발생했습니다. 이미 닫혔을 수 있어 무시합니다.", exception);
		}
	}
}
