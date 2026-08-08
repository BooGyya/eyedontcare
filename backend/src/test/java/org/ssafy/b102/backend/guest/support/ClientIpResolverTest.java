package org.ssafy.b102.backend.guest.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

	private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
	private static final String REMOTE_ADDR = "10.0.0.1";

	@Test
	void usesRemoteAddressWhenNotProxied() {
		assertThat(ClientIpResolver.resolve(request(null))).isEqualTo(REMOTE_ADDR);
	}

	/** nginx가 두 겹이면 헤더가 체인이 된다. 최초 클라이언트는 항상 첫 항목이다. */
	@Test
	void usesFirstEntryOfForwardedChain() {
		String resolved = ClientIpResolver.resolve(request("203.0.113.7, 10.0.0.2, 10.0.0.1"));

		assertThat(resolved).isEqualTo("203.0.113.7");
	}

	@Test
	void trimsWhitespaceAroundForwardedEntry() {
		assertThat(ClientIpResolver.resolve(request("  203.0.113.7  "))).isEqualTo("203.0.113.7");
	}

	@Test
	void fallsBackToRemoteAddressWhenForwardedHeaderIsEmpty() {
		assertThat(ClientIpResolver.resolve(request("   "))).isEqualTo(REMOTE_ADDR);
	}

	private static MockHttpServletRequest request(String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(REMOTE_ADDR);
		if (forwardedFor != null) {
			request.addHeader(FORWARDED_FOR_HEADER, forwardedFor);
		}

		return request;
	}
}
