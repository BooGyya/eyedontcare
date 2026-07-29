package org.ssafy.b102.backend.user.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.ssafy.b102.backend.user.entity.SocialAccount;
import org.ssafy.b102.backend.user.enums.SocialProvider;

public interface SocialAccountRepository
    extends JpaRepository<SocialAccount, Long> {

    @Query("""
        select socialAccount
        from SocialAccount socialAccount
        join fetch socialAccount.user user
        where socialAccount.provider = :provider
          and socialAccount.providerUserId = :providerUserId
          and user.deletedAt is null
        """)
    Optional<SocialAccount> findActiveAccount(
        @Param("provider")
        SocialProvider provider,
        @Param("providerUserId")
        String providerUserId
    );

    @Modifying
    @Query("""
        delete from SocialAccount socialAccount
        where socialAccount.user.id = :userId
        """)
    int deleteByUserId(@Param("userId") Long userId);
}
