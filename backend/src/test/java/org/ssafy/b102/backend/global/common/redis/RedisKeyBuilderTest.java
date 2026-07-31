package org.ssafy.b102.backend.global.common.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RedisKeyBuilderTest {

	@Test
	void buildsKeyWithNamespaceProfileAndDomain() {
		RedisKeyBuilder keyBuilder = new RedisKeyBuilder("dev");

		String key = keyBuilder.build("matchmaking", "queue", "HOCKEY");

		assertThat(key).isEqualTo("edc:dev:matchmaking:queue:hockey");
	}

	/**
	 * 참가자 키는 {@code USER:1} 형식이라 소문자로 낮추면 규약의 {@code :} 구분과 그대로 맞는다.
	 */
	@Test
	void keepsColonInsideSegment() {
		RedisKeyBuilder keyBuilder = new RedisKeyBuilder("dev");

		String key = keyBuilder.build("matchmaking", "entry", "USER:1");

		assertThat(key).isEqualTo("edc:dev:matchmaking:entry:user:1");
	}

	@Test
	void usesConfiguredProfile() {
		RedisKeyBuilder keyBuilder = new RedisKeyBuilder("prod");

		String key = keyBuilder.build("matchmaking", "queue", "BLINK");

		assertThat(key).isEqualTo("edc:prod:matchmaking:queue:blink");
	}

	@Test
	void buildsKeyWithoutSegments() {
		RedisKeyBuilder keyBuilder = new RedisKeyBuilder("dev");

		String key = keyBuilder.build("matchmaking");

		assertThat(key).isEqualTo("edc:dev:matchmaking");
	}

	@Test
	void rejectsBlankDomain() {
		RedisKeyBuilder keyBuilder = new RedisKeyBuilder("dev");

		assertThatThrownBy(() -> keyBuilder.build(" ", "queue"))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsBlankSegment() {
		RedisKeyBuilder keyBuilder = new RedisKeyBuilder("dev");

		assertThatThrownBy(() -> keyBuilder.build("matchmaking", "queue", ""))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsBlankProfile() {
		assertThatThrownBy(() -> new RedisKeyBuilder(""))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
