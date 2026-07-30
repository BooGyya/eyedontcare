package org.ssafy.b102.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.user.dto.request.UserUpdateRequest;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.enums.ProfileImageCode;
import org.ssafy.b102.backend.user.exception.UserErrorCode;
import org.ssafy.b102.backend.user.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class UserUpdateRollbackIntegrationTest {

    @MockitoSpyBean
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Test
    void dbNicknameConflictRollsBackAllProfileChanges() {
        String suffix = UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8);
        User target = userRepository.saveAndFlush(
            User.createLocal(
                "target-" + suffix + "@example.com",
                "encoded-password",
                "T" + suffix
            )
        );
        User owner = userRepository.saveAndFlush(
            User.createLocal(
                "owner-" + suffix + "@example.com",
                "encoded-password",
                "O" + suffix
            )
        );

        try {
            doReturn(false)
                .when(userRepository)
                .existsByNicknameAndIdNotAndDeletedAtIsNull(
                    owner.getNickname(),
                    target.getId()
                );

            UserUpdateRequest request =
                new UserUpdateRequest();
            request.setNickname(owner.getNickname());
            request.setProfileImageCode("PROFILE_2");

            assertThatThrownBy(() ->
                userService.updateMyInfo(
                    target.getId(),
                    target.getId(),
                    request
                )
            ).isInstanceOfSatisfying(
                BusinessException.class,
                exception -> assertThat(exception.getErrorCode())
                    .isEqualTo(
                        UserErrorCode.NICKNAME_DUPLICATED
                    )
            );

            User reloaded = userRepository
                .findById(target.getId())
                .orElseThrow();

            assertThat(reloaded.getNickname())
                .isEqualTo("T" + suffix);
            assertThat(reloaded.getProfileImageCode())
                .isEqualTo(ProfileImageCode.PROFILE_1);
        } finally {
            userRepository.deleteAllById(
                java.util.List.of(
                    target.getId(),
                    owner.getId()
                )
            );
            userRepository.flush();
        }
    }
}
