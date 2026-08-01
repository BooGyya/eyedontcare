package org.ssafy.b102.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 접근 설정.
 *
 * <p>Spring Boot가 기본으로 등록하는 {@code RedisTemplate}은 값을 Java 직렬화로 저장한다.
 * 팀 공통 규약이 Java 직렬화를 금지하므로 키와 값 모두 문자열로 다루는
 * {@link StringRedisTemplate}만 명시적으로 등록해 사용한다.
 * 객체는 도메인 계층에서 JSON 문자열로 변환한 뒤 저장한다.
 */
@Configuration(proxyBeanMethods = false)
public class RedisConfig {

	@Bean
	public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
		return new StringRedisTemplate(connectionFactory);
	}
}
