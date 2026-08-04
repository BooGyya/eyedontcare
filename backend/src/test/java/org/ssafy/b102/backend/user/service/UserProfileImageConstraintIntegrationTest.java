package org.ssafy.b102.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.ssafy.b102.backend.user.dto.request.UserUpdateRequest;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.enums.ProfileImageCode;
import org.ssafy.b102.backend.user.repository.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
class UserProfileImageConstraintIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private Long userId;

    @AfterEach
    void deleteTestUser() {
        if (userId == null) {
            return;
        }

        userRepository.deleteById(userId);
        userRepository.flush();
    }

    @Test
    void profileImageCodesFiveAndEightArePersisted() {
        String suffix = UUID.randomUUID().toString();
        User user = userRepository.saveAndFlush(
            User.createLocal(
                "profile-image-" + suffix + "@example.com",
                "encoded-password",
                "P" + suffix.replace("-", "").substring(0, 9)
            )
        );
        userId = user.getId();

        updateProfileImage("PROFILE_5");

        assertThat(userRepository.findById(userId).orElseThrow()
            .getProfileImageCode())
            .isEqualTo(ProfileImageCode.PROFILE_5);

        updateProfileImage("PROFILE_8");

        assertThat(userRepository.findById(userId).orElseThrow()
            .getProfileImageCode())
            .isEqualTo(ProfileImageCode.PROFILE_8);
    }

    private void updateProfileImage(String profileImageCode) {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setProfileImageCode(profileImageCode);

        userService.updateMyInfo(userId, userId, request);
        userRepository.flush();
    }
}
