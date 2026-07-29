package org.ssafy.b102.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import org.ssafy.b102.backend.global.common.entity.BaseTimeEntity;
import org.ssafy.b102.backend.user.enums.ProfileImageCode;

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_users_email",
            columnNames = "email"
        ),
        @UniqueConstraint(
            name = "uk_users_nickname",
            columnNames = "nickname"
        )
    }
)
public class User extends BaseTimeEntity {

    private static final ProfileImageCode DEFAULT_PROFILE_IMAGE_CODE =
        ProfileImageCode.PROFILE_1;
    private static final String WITHDRAWN_NICKNAME_PREFIX =
        "withdrawn-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "nickname", nullable = false, length = 50)
    private String nickname;

    @Column(name = "password", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_img_code", nullable = false, length = 20)
    private ProfileImageCode profileImageCode;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected User() {
    }

    private User(
        String email,
        String passwordHash,
        String nickname,
        ProfileImageCode profileImageCode
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.profileImageCode = profileImageCode;
    }

    public static User createLocal(
        String email,
        String passwordHash,
        String nickname
    ) {
        Objects.requireNonNull(email, "email은 null일 수 없습니다.");
        Objects.requireNonNull(passwordHash, "passwordHash는 null일 수 없습니다.");
        Objects.requireNonNull(nickname, "nickname은 null일 수 없습니다.");

        return new User(
            email,
            passwordHash,
            nickname,
            DEFAULT_PROFILE_IMAGE_CODE
        );
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public ProfileImageCode getProfileImageCode() {
        return profileImageCode;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void withdraw(Instant withdrawnAt) {
        Objects.requireNonNull(
            withdrawnAt,
            "withdrawnAt은 null일 수 없습니다."
        );

        if (isDeleted()) {
            return;
        }

        if (id == null) {
            throw new IllegalStateException(
                "저장되지 않은 사용자는 탈퇴할 수 없습니다."
            );
        }

        email = null;
        passwordHash = null;
        nickname = WITHDRAWN_NICKNAME_PREFIX + id;
        profileImageCode = DEFAULT_PROFILE_IMAGE_CODE;
        deletedAt = withdrawnAt;
    }
}
