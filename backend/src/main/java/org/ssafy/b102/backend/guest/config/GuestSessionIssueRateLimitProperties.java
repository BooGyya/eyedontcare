package org.ssafy.b102.backend.guest.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 게스트 세션 발급 엔드포인트의 요청 제한 설정.
 *
 * <p>발급 엔드포인트는 인증 없이 열려 있어서, 제한이 없으면 스크립트 하나로 세션과 그에 딸린
 * 게임 결과 레코드를 무한정 만들 수 있다. 랭킹은 회원({@code userId})만 집계하므로 오염되지
 * 않지만 저장소는 그대로 쌓인다.
 *
 * <p>{@code window} 동안 같은 클라이언트가 {@code limit}회까지 발급할 수 있다. 사람은 탭을 열
 * 때마다 한 번 호출하므로 넉넉한 값이어도 충분하고, 공유 IP(NAT) 환경에서 여러 명이 동시에
 * 접속하는 경우를 막지 않을 만큼은 여유를 둬야 한다.
 */
@ConfigurationProperties(prefix = "app.guest.issue-rate-limit")
public record GuestSessionIssueRateLimitProperties(int limit, Duration window) {

	public GuestSessionIssueRateLimitProperties {
		if (limit <= 0) {
			throw new IllegalArgumentException("Guest session issue limit must be positive");
		}
		if (window == null || window.isZero() || window.isNegative()) {
			throw new IllegalArgumentException("Guest session issue window must be positive");
		}
	}
}
