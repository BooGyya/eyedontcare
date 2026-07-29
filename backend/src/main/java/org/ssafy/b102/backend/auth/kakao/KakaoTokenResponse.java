package org.ssafy.b102.backend.auth.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoTokenResponse(
    @JsonProperty("access_token")
    String accessToken
) {
}
