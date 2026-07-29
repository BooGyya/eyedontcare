package org.ssafy.b102.backend.user.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.ssafy.b102.backend.user.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    boolean existsByIdAndDeletedAtIsNull(Long id);
}
