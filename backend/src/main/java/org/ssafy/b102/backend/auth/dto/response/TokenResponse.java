package org.ssafy.b102.backend.auth.dto.response;

import org.ssafy.b102.backend.global.security.jwt.TokenPair;

public record TokenResponse(
    String accessToken,
    String refreshToken
) {

    public static TokenResponse from(TokenPair tokenPair) {
        return new TokenResponse(
            tokenPair.accessToken(),
            tokenPair.refreshToken()
        );
    }
}
