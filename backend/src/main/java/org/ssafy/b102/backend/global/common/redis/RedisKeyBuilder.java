package org.ssafy.b102.backend.global.common.redis;

import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Redis 키를 팀 공통 규약에 맞게 생성한다.
 *
 * <p>규약은 {@code edc:<profile>:<domain>:<resource>:<identifier>} 형식이며 소문자와 {@code :}만 사용한다.
 * 프로파일을 키에 넣기 때문에 dev와 prod가 같은 Redis 인스턴스를 공유해도 키가 섞이지 않는다.
 *
 * <p>참가자 키처럼 값 자체에 {@code :}가 포함된 경우({@code USER:1})는 그대로 유지된다.
 * 소문자로 낮추면 {@code user:1}이 되어 규약의 구분자와 자연스럽게 맞는다.
 */
@Component
public class RedisKeyBuilder {

	private static final String NAMESPACE = "edc";
	private static final String DELIMITER = ":";

	private final String profile;

	/**
	 * @param profile 활성 프로파일. 프로파일을 여러 개 활성화하는 구성은 가정하지 않는다.
	 */
	public RedisKeyBuilder(@Value("${spring.profiles.active:dev}") String profile) {
		this.profile = normalize(profile, "프로파일");
	}

	public String build(String domain, String... segments) {
		StringBuilder key = new StringBuilder(NAMESPACE)
			.append(DELIMITER)
			.append(profile)
			.append(DELIMITER)
			.append(normalize(domain, "도메인"));

		for (String segment : segments) {
			key.append(DELIMITER).append(normalize(segment, "키 세그먼트"));
		}

		return key.toString();
	}

	private static String normalize(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + "은(는) 비어 있을 수 없습니다.");
		}

		return value.trim().toLowerCase(Locale.ROOT);
	}
}
