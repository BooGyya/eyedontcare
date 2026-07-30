package org.ssafy.b102.backend.user.service;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.user.dto.response.UserResponse;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.enums.SocialProvider;
import org.ssafy.b102.backend.user.enums.UserLoginType;
import org.ssafy.b102.backend.user.exception.UserErrorCode;
import org.ssafy.b102.backend.user.repository.SocialAccountRepository;
import org.ssafy.b102.backend.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final SocialAccountRepository socialAccountRepository;

    public UserService(
        UserRepository userRepository,
        SocialAccountRepository socialAccountRepository
    ) {
        this.userRepository = userRepository;
        this.socialAccountRepository = socialAccountRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getMyInfo(
        Long requestedUserId,
        Long authenticatedUserId
    ) {
        validateSelf(requestedUserId, authenticatedUserId);

        User user = userRepository
            .findByIdAndDeletedAtIsNull(requestedUserId)
            .orElseThrow(() ->
                new BusinessException(
                    UserErrorCode.USER_NOT_FOUND
                ));

        SocialProvider socialProvider = socialAccountRepository
            .findProviderByUserId(user.getId())
            .orElse(null);

        return UserResponse.from(
            user,
            UserLoginType.from(socialProvider)
        );
    }

    private void validateSelf(
        Long requestedUserId,
        Long authenticatedUserId
    ) {
        if (
            requestedUserId == null ||
            !Objects.equals(
                requestedUserId,
                authenticatedUserId
            )
        ) {
            throw new BusinessException(
                UserErrorCode.USER_ACCESS_DENIED
            );
        }
    }
}
