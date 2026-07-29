package org.ssafy.b102.backend.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.user.enums.ProfileImageCode;
import org.ssafy.b102.backend.user.enums.SocialProvider;

class SocialAccountTest {

    @Test
    void 카카오_회원과_소셜_계정을_생성한다() {
        User user = User.createSocial("nickname");

        SocialAccount account = SocialAccount.create(
            user,
            SocialProvider.KAKAO,
            "12345"
        );

        assertThat(user.getEmail()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getProfileImageCode())
            .isEqualTo(ProfileImageCode.PROFILE_1);
        assertThat(account.getUser()).isSameAs(user);
        assertThat(account.getProvider())
            .isEqualTo(SocialProvider.KAKAO);
        assertThat(account.getProviderUserId())
            .isEqualTo("12345");
    }

    @Test
    void 플랫폼_사용자_ID는_비어_있을_수_없다() {
        User user = User.createSocial("nickname");

        assertThatThrownBy(
            () -> SocialAccount.create(
                user,
                SocialProvider.KAKAO,
                " "
            )
        ).isInstanceOf(IllegalArgumentException.class);
    }
}
