package org.ssafy.b102.backend.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.ssafy.b102.backend.global.common.entity.BaseTimeEntity;
import org.ssafy.b102.backend.user.enums.ProfileImageCode;

class UserTest {

    private static final Long USER_ID = 123L;

    @Test
    void 프로필을_수정하면_허용된_필드만_변경한다() {
        User user = User.createLocal(
            "user@example.com",
            "encoded-password",
            "Original1"
        );

        user.updateProfile(
            "NewName",
            ProfileImageCode.PROFILE_3
        );

        assertThat(user.getNickname()).isEqualTo("NewName");
        assertThat(user.getProfileImageCode())
            .isEqualTo(ProfileImageCode.PROFILE_3);
        assertThat(user.getEmail())
            .isEqualTo("user@example.com");
        assertThat(user.getPasswordHash())
            .isEqualTo("encoded-password");
    }

    @Test
    void 비밀번호는_인코딩된_값으로만_변경하고_다른_필드를_유지한다() {
        User user = User.createLocal(
            "user@example.com",
            "old-hash",
            "Nickname1"
        );

        user.changePassword("new-hash");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getEmail())
            .isEqualTo("user@example.com");
        assertThat(user.getNickname()).isEqualTo("Nickname1");
        assertThat(user.getProfileImageCode())
            .isEqualTo(ProfileImageCode.PROFILE_1);
    }

    @Test
    void null_비밀번호_해시는_거부한다() {
        User user = User.createLocal(
            "user@example.com",
            "old-hash",
            "Nickname1"
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> user.changePassword(null)
        ).isInstanceOf(NullPointerException.class);
    }

    @Test
    void 탈퇴하면_개인정보를_익명화하고_식별자와_생성시각을_유지한다() {
        Instant createdAt =
            Instant.parse("2026-07-01T00:00:00Z");
        Instant withdrawnAt =
            Instant.parse("2026-07-30T00:00:00Z");

        User user = User.createLocal(
            "user@example.com",
            "encoded-password",
            "nickname"
        );

        setField(User.class, user, "id", USER_ID);
        setField(
            User.class,
            user,
            "profileImageCode",
            ProfileImageCode.PROFILE_3
        );
        setField(
            BaseTimeEntity.class,
            user,
            "createdAt",
            createdAt
        );

        user.withdraw(withdrawnAt);

        assertThat(user.getId()).isEqualTo(USER_ID);
        assertThat(user.getCreatedAt()).isEqualTo(createdAt);
        assertThat(user.getDeletedAt()).isEqualTo(withdrawnAt);
        assertThat(user.getEmail()).isNull();
        assertThat(user.getPasswordHash()).isNull();
        assertThat(user.getNickname())
            .isEqualTo("withdrawn-" + USER_ID)
            .hasSizeLessThanOrEqualTo(50);
        assertThat(user.getProfileImageCode())
            .isEqualTo(ProfileImageCode.PROFILE_1);
    }

    @Test
    void 이미_탈퇴한_사용자는_최초_탈퇴_상태를_유지한다() {
        Instant firstWithdrawal =
            Instant.parse("2026-07-30T00:00:00Z");
        Instant secondWithdrawal =
            Instant.parse("2026-07-30T01:00:00Z");

        User user = User.createLocal(
            "user@example.com",
            "encoded-password",
            "nickname"
        );
        setField(User.class, user, "id", USER_ID);

        user.withdraw(firstWithdrawal);
        user.withdraw(secondWithdrawal);

        assertThat(user.getDeletedAt())
            .isEqualTo(firstWithdrawal);
        assertThat(user.getNickname())
            .isEqualTo("withdrawn-" + USER_ID);
    }

    private void setField(
        Class<?> declaringClass,
        Object target,
        String fieldName,
        Object value
    ) {
        try {
            Field field =
                declaringClass.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (
            NoSuchFieldException |
            IllegalAccessException exception
        ) {
            throw new IllegalStateException(
                "테스트 필드 설정에 실패했습니다.",
                exception
            );
        }
    }
}
