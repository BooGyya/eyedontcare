package org.ssafy.b102.backend.matchmaking.websocket;

/**
 * 클라이언트가 연결 직후 보내는 인증 프레임.
 *
 * <p>브라우저는 WebSocket 핸드셰이크에 커스텀 헤더나 {@code Authorization}을 붙일 수 없다.
 * 그래서 연결을 익명으로 맺고 첫 프레임으로 인증 정보를 보낸다. 쿼리 파라미터는 URL·프록시 로그에
 * 값이 남으므로 쓰지 않는다.
 *
 * <p>회원은 {@code accessToken}(JWT)을, 게스트는 {@code guestSessionId}(UUID)를 담는다.
 * 핸드셰이크가 열려 있다고 인증된 것은 아니며, 서버가 이 프레임에서 토큰·세션을 검증한다.
 * WebSocket 흐름에서는 게스트 세션을 새로 발급하지 않는다.
 */
public record MatchAuthFrame(String type, String accessToken, String guestSessionId) {
}
