package org.ssafy.b102.backend.user.dto.response;

public record NicknameCheckResponse(
    String nickname,
    boolean available
) {
}
