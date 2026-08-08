package org.ssafy.b102.backend.guest.support;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 요청을 보낸 클라이언트의 IP를 푼다. 발급 요청 제한의 기준값으로만 쓴다.
 *
 * <p>이 앱은 nginx 뒤에 있어서 {@code getRemoteAddr()}가 프록시 컨테이너의 IP를 돌려준다.
 * 그대로 쓰면 모든 사용자가 한 IP로 보여 제한이 무의미하다. 그래서 nginx가 붙여 주는
 * {@code X-Forwarded-For}의 **첫 항목**(최초 클라이언트)을 우선 사용한다.
 *
 * <p>⚠️ 이 값은 신뢰할 수 없다. nginx는 {@code $proxy_add_x_forwarded_for}로 기존 헤더 뒤에
 * 덧붙이기만 하므로, 클라이언트가 가짜 {@code X-Forwarded-For}를 미리 넣어 보내면 첫 항목을
 * 마음대로 정할 수 있다. 즉 여기서의 제한은 실수·단순 스크립트를 막는 안전망이지 공격 방어가
 * 아니다. 제대로 막으려면 nginx에 {@code real_ip_module}({@code set_real_ip_from} +
 * {@code real_ip_recursive})을 설정해 신뢰 프록시 구간을 지정해야 한다.
 */
public final class ClientIpResolver {

	private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
	private static final String FORWARDED_FOR_DELIMITER = ",";

	private ClientIpResolver() {
	}

	public static String resolve(HttpServletRequest request) {
		String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
		if (forwardedFor != null) {
			String origin = forwardedFor.split(FORWARDED_FOR_DELIMITER)[0].trim();
			if (!origin.isEmpty()) {
				return origin;
			}
		}

		return request.getRemoteAddr();
	}
}
