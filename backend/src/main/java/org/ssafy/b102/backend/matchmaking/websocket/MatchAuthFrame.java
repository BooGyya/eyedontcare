package org.ssafy.b102.backend.matchmaking.websocket;

/**
 * 클라이언트가 연결 직후 보내는 인증 프레임.
 *
 * <p>브라우저는 WebSocket 핸드셰이크에 커스텀 헤더({@code X-Participant-Key})나 {@code Authorization}을
 * 붙일 수 없다. 그래서 연결을 익명으로 맺고 첫 프레임으로 인증 정보를 보낸다. 쿼리 파라미터는
 * URL·프록시 로그에 값이 남으므로 쓰지 않는다.
 *
 * <p>현재는 임시 인증 규약을 따라 {@code participantKey}를 담는다. 인증 도메인이 완성되면
 * 이 프레임의 필드가 JWT로 바뀐다. 형태는 auth 담당자와 합의가 필요하다.
 */
public record MatchAuthFrame(String type, String participantKey) {
}
