package org.ssafy.b102.backend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

	private static final String API_PATH_PATTERN = "/api/**";
	private static final long PREFLIGHT_CACHE_SECONDS = 3600L;
	private static final String[] ALLOWED_METHODS = {
		"GET",
		"POST",
        "PUT",
		"PATCH",
		"DELETE",
		"OPTIONS"
	};
	private static final String[] ALLOWED_HEADERS = {
		"Authorization",
		"Content-Type",
		"X-Guest-Session-Id"
	};

	private final String[] allowedOrigins;

	public CorsConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins.clone();
	}

	@Bean
	public CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(java.util.List.of(allowedOrigins));
		configuration.setAllowedMethods(java.util.List.of(ALLOWED_METHODS));
		configuration.setAllowedHeaders(java.util.List.of(ALLOWED_HEADERS));
		configuration.setAllowCredentials(false);
		configuration.setMaxAge(PREFLIGHT_CACHE_SECONDS);

		UrlBasedCorsConfigurationSource source =
			new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration(
			API_PATH_PATTERN,
			configuration
		);

		return source;
	}
}
