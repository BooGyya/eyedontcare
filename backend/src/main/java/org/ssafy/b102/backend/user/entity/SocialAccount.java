package org.ssafy.b102.backend.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.ssafy.b102.backend.user.enums.SocialProvider;

@Entity
@Table(
    name = "social_accounts",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_social_accounts_user",
            columnNames = "user_id"
        ),
        @UniqueConstraint(
            name = "uk_social_accounts_provider_identity",
            columnNames = {
                "provider",
                "provider_user_id"
            }
        )
    }
)
@EntityListeners(AuditingEntityListener.class)
public class SocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "user_id",
        nullable = false,
        unique = true
    )
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private SocialProvider provider;

    @Column(
        name = "provider_user_id",
        nullable = false,
        length = 255
    )
    private String providerUserId;

    @CreatedDate
    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    protected SocialAccount() {
    }

    private SocialAccount(
        User user,
        SocialProvider provider,
        String providerUserId
    ) {
        this.user = user;
        this.provider = provider;
        this.providerUserId = providerUserId;
    }

    public static SocialAccount create(
        User user,
        SocialProvider provider,
        String providerUserId
    ) {
        Objects.requireNonNull(
            user,
            "user는 null일 수 없습니다."
        );
        Objects.requireNonNull(
            provider,
            "provider는 null일 수 없습니다."
        );

        if (
            providerUserId == null ||
            providerUserId.isBlank()
        ) {
            throw new IllegalArgumentException(
                "providerUserId는 비어 있을 수 없습니다."
            );
        }

        return new SocialAccount(
            user,
            provider,
            providerUserId
        );
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public SocialProvider getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
