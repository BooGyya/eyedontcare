package org.ssafy.b102.testfixture.websocket;

import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketExtension;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * 레지스트리·핸들러·알림 로직을 Spring 없이 검증하기 위한 손으로 만든 스텁.
 *
 * <p>보낸 텍스트 프레임과 종료 상태를 기록한다. 저장소에 Mockito 의존성이 없어
 * 컨트롤러·핸들러 테스트는 손으로 만든 스텁으로 작성하는 규약을 따른다.
 */
public class StubWebSocketSession implements WebSocketSession {

	private final String id;
	private final Map<String, Object> attributes = new HashMap<>();
	private final List<String> sentPayloads = new ArrayList<>();

	private boolean open = true;
	private CloseStatus closeStatus;

	public StubWebSocketSession(String id) {
		this.id = id;
	}

	public List<String> sentPayloads() {
		return List.copyOf(sentPayloads);
	}

	public String lastSentPayload() {
		if (sentPayloads.isEmpty()) {
			throw new IllegalStateException("보낸 프레임이 없습니다.");
		}

		return sentPayloads.get(sentPayloads.size() - 1);
	}

	public CloseStatus closeStatus() {
		return closeStatus;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	@Override
	public void sendMessage(WebSocketMessage<?> message) {
		if (!open) {
			throw new IllegalStateException("닫힌 세션에는 전송할 수 없습니다.");
		}
		if (message instanceof TextMessage textMessage) {
			sentPayloads.add(textMessage.getPayload());
			return;
		}

		throw new UnsupportedOperationException("텍스트 프레임만 지원합니다.");
	}

	@Override
	public boolean isOpen() {
		return open;
	}

	@Override
	public void close() {
		close(CloseStatus.NORMAL);
	}

	@Override
	public void close(CloseStatus status) {
		this.open = false;
		this.closeStatus = status;
	}

	@Override
	public URI getUri() {
		throw new UnsupportedOperationException();
	}

	@Override
	public HttpHeaders getHandshakeHeaders() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Principal getPrincipal() {
		throw new UnsupportedOperationException();
	}

	@Override
	public InetSocketAddress getLocalAddress() {
		throw new UnsupportedOperationException();
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		throw new UnsupportedOperationException();
	}

	@Override
	public String getAcceptedProtocol() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setTextMessageSizeLimit(int messageSizeLimit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getTextMessageSizeLimit() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setBinaryMessageSizeLimit(int messageSizeLimit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int getBinaryMessageSizeLimit() {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<WebSocketExtension> getExtensions() {
		throw new UnsupportedOperationException();
	}
}
