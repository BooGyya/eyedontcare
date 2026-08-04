package org.ssafy.b102.backend.game.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSAFY GMS(OpenAI 호환 게이트웨이) 연동 설정.
 *
 * <p>{@code apiKey}는 비밀값이라 소스에 두지 않고 환경변수({@code GMS_API_KEY})로 주입한다.
 * 값이 비어 있으면 그림 인식 기능은 "미설정" 오류로 응답한다.
 */
@ConfigurationProperties(prefix = "gms")
public record GmsProperties(
	String baseUri,
	String apiKey,
	String model,
	Duration connectTimeout,
	Duration readTimeout
) {

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}
}
