package org.ssafy.b102.backend.user.enums;

public enum UserLoginType {
    LOCAL,
    KAKAO;

    public static UserLoginType from(
        SocialProvider socialProvider
    ) {
        if (socialProvider == null) {
            return LOCAL;
        }

        return switch (socialProvider) {
            case KAKAO -> KAKAO;
        };
    }
}
