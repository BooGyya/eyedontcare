package org.ssafy.b102.backend.global.security.jwt;

public record TokenPair(
    String accessToken,
    String refreshToken
) {
}
