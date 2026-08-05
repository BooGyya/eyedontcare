package org.ssafy.b102.backend.gamesession.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.ssafy.b102.backend.global.security.jwt.JwtTokenProvider;
import org.ssafy.b102.backend.guest.service.GuestSessionService;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.repository.UserRepository;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GameSessionWebSocketServiceTest {

	private static final UUID ROOM_ID = UUID.randomUUID();

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private GuestSessionService guestSessionService;

	@Mock
	private UserRepository userRepository;

	private GameSessionWebSocketService service;

	@BeforeEach
	void setUp() {
		service = new GameSessionWebSocketService(
			jwtTokenProvider,
			guestSessionService,
			userRepository,
			JsonMapper.builder().build()
		);
	}

	@Test
	void 인증하면_참가자_닉네임을_담은_SESSION_STATE를_보낸다() throws Exception {
		User user = userWithNickname("눈사람");
		when(jwtTokenProvider.parseAccessTokenUserId("tok"))
			.thenReturn(Optional.of(1L));
		when(userRepository.findByIdAndDeletedAtIsNull(1L))
			.thenReturn(Optional.of(user));
		WebSocketSession session = session();

		service.connectionEstablished(session, ROOM_ID);
		service.handleMessage(session, authFrame("tok"));

		ArgumentCaptor<TextMessage> captor =
			ArgumentCaptor.forClass(TextMessage.class);
		verify(session).sendMessage(captor.capture());
		assertThat(captor.getValue().getPayload())
			.contains("SESSION_STATE")
			.contains("USER:1")
			.contains("눈사람");
	}

	@Test
	void 상대의_PLAYER_EVENT를_중계하고_본인은_받지_않는다() throws Exception {
		stubMember("a", 1L, "눈사람");
		stubMember("b", 2L, "반짝콩");
		WebSocketSession a = session();
		WebSocketSession b = session();
		authenticate(a, "a");
		authenticate(b, "b");
		clearInvocations(a, b);

		service.handleMessage(
			a,
			"{\"type\":\"PLAYER_EVENT\",\"eventType\":\"BLINK_COUNT\","
				+ "\"payload\":{\"count\":3}}"
		);

		ArgumentCaptor<TextMessage> captor =
			ArgumentCaptor.forClass(TextMessage.class);
		verify(b).sendMessage(captor.capture());
		assertThat(captor.getValue().getPayload())
			.contains("PLAYER_EVENT")
			.contains("BLINK_COUNT")
			.contains("USER:1");
		verify(a, never()).sendMessage(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void 연결이_끊기면_상대에게_PARTICIPANT_LEFT를_보낸다() throws Exception {
		stubMember("a", 1L, "눈사람");
		stubMember("b", 2L, "반짝콩");
		WebSocketSession a = session();
		WebSocketSession b = session();
		authenticate(a, "a");
		authenticate(b, "b");
		clearInvocations(a, b);

		service.connectionClosed(b);

		ArgumentCaptor<TextMessage> captor =
			ArgumentCaptor.forClass(TextMessage.class);
		verify(a).sendMessage(captor.capture());
		assertThat(captor.getValue().getPayload())
			.contains("PARTICIPANT_LEFT")
			.contains("USER:2");
	}

	@Test
	void GAME_OVER를_먼저_보낸_뒤_끊기면_PARTICIPANT_LEFT를_보내지_않는다() throws Exception {
		stubMember("a", 1L, "눈사람");
		stubMember("b", 2L, "반짝콩");
		WebSocketSession a = session();
		WebSocketSession b = session();
		authenticate(a, "a");
		authenticate(b, "b");

		// b가 정상 종료(GAME_OVER)를 보낸 뒤 소켓이 닫힌다.
		service.handleMessage(
			b,
			"{\"type\":\"PLAYER_EVENT\",\"eventType\":\"GAME_OVER\"}"
		);
		clearInvocations(a, b);

		service.connectionClosed(b);

		// 정상 종료였으므로 이탈(PARTICIPANT_LEFT) 통지를 보내지 않는다.
		verify(a, never()).sendMessage(org.mockito.ArgumentMatchers.any());
	}

	private void stubMember(String token, long userId, String nickname) {
		User user = userWithNickname(nickname);
		when(jwtTokenProvider.parseAccessTokenUserId(token))
			.thenReturn(Optional.of(userId));
		when(userRepository.findByIdAndDeletedAtIsNull(userId))
			.thenReturn(Optional.of(user));
	}

	private static User userWithNickname(String nickname) {
		User user = mock(User.class);
		when(user.getNickname()).thenReturn(nickname);
		return user;
	}

	private void authenticate(WebSocketSession session, String token) {
		service.connectionEstablished(session, ROOM_ID);
		service.handleMessage(session, authFrame(token));
	}

	private static String authFrame(String accessToken) {
		return "{\"type\":\"AUTH\",\"accessToken\":\"" + accessToken + "\"}";
	}

	private static WebSocketSession session() {
		WebSocketSession session = mock(WebSocketSession.class);
		when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
		lenient().when(session.isOpen()).thenReturn(true);
		return session;
	}
}
