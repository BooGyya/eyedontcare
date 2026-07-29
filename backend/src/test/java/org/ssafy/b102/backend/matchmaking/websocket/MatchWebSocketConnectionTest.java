package org.ssafy.b102.backend.matchmaking.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 연결 자체를 실제 서버로 한 번 확인한다. 등록·알림 로직은 단위 테스트가 담당하므로,
 * 여기서는 {@code /ws/match} 핸드셰이크가 성공하고 우리 핸들러가 붙어 있는지만 본다.
 *
 * <p>StandardWebSocketClient는 Origin 헤더를 보내지 않는다. {@code setAllowed-origins}로 특정
 * 오리진을 지정해도 Origin이 없는(비브라우저) 요청은 허용되므로 핸드셰이크가 성공한다.
 *
 * <p>잘못된 프레임을 보내면 우리 핸들러가 연결을 닫는다. 그 종료를 관찰해 "붙어 있는 것이
 * 우리 핸들러"임을 확인한다. Redis·PostgreSQL 컨테이너가 필요하다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MatchWebSocketConnectionTest {

	@Value("${local.server.port}")
	private int port;

	@Test
	void handshakeSucceedsAndHandlerClosesMalformedFrame() throws Exception {
		StandardWebSocketClient client = new StandardWebSocketClient();
		CountDownLatch established = new CountDownLatch(1);
		CountDownLatch closed = new CountDownLatch(1);

		WebSocketSession session = client.execute(
			new TextWebSocketHandler() {
				@Override
				public void afterConnectionEstablished(WebSocketSession session) {
					established.countDown();
				}

				@Override
				public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
					closed.countDown();
				}
			},
			"ws://localhost:" + port + "/ws/match"
		).get(5, TimeUnit.SECONDS);

		assertThat(established.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(session.isOpen()).isTrue();

		session.sendMessage(new TextMessage("not-json"));

		assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
	}
}
