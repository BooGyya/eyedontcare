package org.ssafy.b102.backend.auth.kakao;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
    String clientId,
    String clientSecret,
    String redirectUri,
    String tokenUri,
    String userInfoUri,
    Duration connectTimeout,
    Duration readTimeout
) {
}
