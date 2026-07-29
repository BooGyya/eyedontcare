package org.ssafy.b102.backend.auth.dto.response;

import org.ssafy.b102.backend.global.security.jwt.TokenPair;

public record SignupResponse(
    String accessToken,
    String refreshToken
) {

    public static SignupResponse from(TokenPair tokenPair) {
        return new SignupResponse(
            tokenPair.accessToken(),
            tokenPair.refreshToken()
        );
    }
}
