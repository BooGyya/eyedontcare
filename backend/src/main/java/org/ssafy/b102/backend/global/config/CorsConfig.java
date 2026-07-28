package org.ssafy.b102.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

	private static final String API_PATH_PATTERN = "/api/**";
	private static final long PREFLIGHT_CACHE_SECONDS = 3600L;

	private final String[] allowedOrigins;

	public CorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins.clone();
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping(API_PATH_PATTERN)
			.allowedOrigins(allowedOrigins)
			.allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
			.allowedHeaders("Authorization", "Content-Type")
			.allowCredentials(false)
			.maxAge(PREFLIGHT_CACHE_SECONDS);
	}
}
