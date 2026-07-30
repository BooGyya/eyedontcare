package org.ssafy.b102.backend.matchmaking.websocket;

import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.TextMessage;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.matchmaking.entity.MatchStatus;
import org.ssafy.b102.backend.matchmaking.service.MatchNotifier;
import org.ssafy.b102.backend.matchmaking.service.MatchmakingService;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.guest.support.GuestParticipantKey;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code /ws/match} 핸들러. 익명으로 연결을 맺고 첫 프레임으로 인증한다.
 *
 * <p>구독 개념 없이 특정 두 명에게 한 번 보내면 끝이므로 STOMP가 아니라 raw WebSocket을 쓴다.
 * STOMP를 쓰면 프레임에 STOMP 헤더가 붙어 명세({@code MATCH_SUCCESS})와 달라진다.
 *
 * <p>인증은 첫 프레임에서 한다. 회원은 {@code accessToken}(JWT)을 파싱해 {@code USER:{userId}}로,
 * 게스트는 {@code guestSessionId}를 Redis에서 검증해 {@code GUEST:{uuid}}로 푼다. 게스트 세션은
 * 여기서 새로 발급하지 않는다(발급은 매칭 신청 단계의 몫이다).
 *
 * <p>연결 순서상 클라이언트는 {@code /api/v1/match/join} 호출 전에 연결해야 푸시를 놓치지 않는다.
 * 늦게 연결해 이미 성사돼 있는 경우를 위해, 인증 직후 상태가 {@code ENTERING_ROOM}이면 즉시
 * {@code MATCH_SUCCESS}를 다시 보낸다. REST 신청 응답도 같은 상태를 담아 fallback이 된다.
 */
@Component
public class MatchWebSocketHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(MatchWebSocketHandler.class);

	private static final String MEMBER_KEY_PREFIX = "USER:";

	/**
	 * 첫 프레임 인증 이후 세션에 심는 표시. 이후 프레임으로 다른 키를 밀어넣는 세션 탈취를 막는다.
	 */
	private static final String AUTHENTICATED_ATTRIBUTE = "matchmaking.authenticated";

	private final MatchmakingService matchmakingService;
	private final MatchSessionRegistry registry;
	private final MatchNotifier matchNotifier;
	private final JwtTokenProvider jwtTokenProvider;
	private final GuestSessionService guestSessionService;
	private final JsonMapper jsonMapper;

	public MatchWebSocketHandler(
		MatchmakingService matchmakingService,
		MatchSessionRegistry registry,
		MatchNotifier matchNotifier,
		JwtTokenProvider jwtTokenProvider,
		GuestSessionService guestSessionService,
		JsonMapper jsonMapper
	) {
		this.matchmakingService = matchmakingService;
		this.registry = registry;
		this.matchNotifier = matchNotifier;
		this.jwtTokenProvider = jwtTokenProvider;
		this.guestSessionService = guestSessionService;
		this.jsonMapper = jsonMapper;
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		if (Boolean.TRUE.equals(session.getAttributes().get(AUTHENTICATED_ATTRIBUTE))) {
			return;
		}

		String participantKey = resolveParticipantKey(message.getPayload());
		if (participantKey == null) {
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

	/**
	 * 인증 프레임에서 참가자 키를 푼다. 신뢰 가능한 출처(서명 검증된 JWT, Redis 세션)에서만 만든다.
	 * 형식·서명·존재 검증에 실패하면 {@code null}을 반환해 연결을 닫게 한다.
	 */
	private String resolveParticipantKey(String payload) {
		MatchAuthFrame frame;
		try {
			frame = jsonMapper.readValue(payload, MatchAuthFrame.class);
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
			return resolveGuestKey(frame.guestSessionId());
		}

		return null;
	}

	private String resolveGuestKey(String guestSessionId) {
		try {
			UUID sessionId = UUID.fromString(guestSessionId);
			guestSessionService.validate(sessionId);

			return new GuestParticipantKey(sessionId).value();
		} catch (IllegalArgumentException | BusinessException exception) {
			return null;
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private void close(WebSocketSession session, CloseStatus status) {
		try {
			session.close(status);
		} catch (IOException exception) {
			log.debug("세션을 닫는 중 예외가 발생했습니다. 이미 닫혔을 수 있어 무시합니다.", exception);
		}
	}
}
