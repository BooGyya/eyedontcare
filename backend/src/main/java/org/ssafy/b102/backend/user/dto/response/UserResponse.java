package org.ssafy.b102.backend.user.dto.response;

import java.time.Instant;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.enums.ProfileImageCode;
import org.ssafy.b102.backend.user.enums.UserLoginType;

public record UserResponse(
    Long id,
    String email,
    String nickname,
    ProfileImageCode profileImageCode,
    UserLoginType loginType,
    Instant createdAt
) {

    public static UserResponse from(
        User user,
        UserLoginType loginType
    ) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getNickname(),
            user.getProfileImageCode(),
            loginType,
            user.getCreatedAt()
        );
    }
}
