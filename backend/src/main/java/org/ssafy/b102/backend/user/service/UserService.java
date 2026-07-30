package org.ssafy.b102.backend.user.service;

import java.util.Objects;
import java.util.regex.Pattern;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ssafy.b102.backend.global.error.BusinessException;
import org.ssafy.b102.backend.global.error.CommonErrorCode;
import org.ssafy.b102.backend.user.dto.request.UserUpdateRequest;
import org.ssafy.b102.backend.user.dto.response.UserResponse;
import org.ssafy.b102.backend.user.dto.response.NicknameCheckResponse;
import org.ssafy.b102.backend.user.entity.User;
import org.ssafy.b102.backend.user.enums.ProfileImageCode;
import org.ssafy.b102.backend.user.enums.SocialProvider;
import org.ssafy.b102.backend.user.enums.UserLoginType;
import org.ssafy.b102.backend.user.exception.UserErrorCode;
import org.ssafy.b102.backend.user.repository.SocialAccountRepository;
import org.ssafy.b102.backend.user.repository.UserRepository;

@Service
public class UserService {

    private static final String NICKNAME_UNIQUE_CONSTRAINT =
        "uk_users_nickname";
    private static final Pattern NICKNAME_PATTERN =
        Pattern.compile("^[가-힣A-Za-z0-9]{2,10}$");

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

        return createUserResponse(user);
    }

    @Transactional
    public UserResponse updateMyInfo(
        Long requestedUserId,
        Long authenticatedUserId,
        UserUpdateRequest request
    ) {
        validateSelf(requestedUserId, authenticatedUserId);

        User user = userRepository
            .findByIdAndDeletedAtIsNull(requestedUserId)
            .orElseThrow(() ->
                new BusinessException(
                    UserErrorCode.USER_NOT_FOUND
                ));

        validateUpdateRequest(request);

        String updatedNickname = user.getNickname();
        if (request.isNicknameProvided()) {
            validateNickname(request.getNickname());
            validateNicknameDuplication(
                request.getNickname(),
                authenticatedUserId
            );
            updatedNickname = request.getNickname();
        }

        ProfileImageCode updatedProfileImageCode =
            user.getProfileImageCode();
        if (request.isProfileImageCodeProvided()) {
            updatedProfileImageCode = parseProfileImageCode(
                request.getProfileImageCode()
            );
        }

        user.updateProfile(
            updatedNickname,
            updatedProfileImageCode
        );
        flushProfileUpdate();

        return createUserResponse(user);
    }

    @Transactional(readOnly = true)
    public NicknameCheckResponse checkNicknameAvailability(
        Long authenticatedUserId,
        String nickname
    ) {
        User user = userRepository
            .findByIdAndDeletedAtIsNull(authenticatedUserId)
            .orElseThrow(() ->
                new BusinessException(
                    UserErrorCode.USER_NOT_FOUND
                ));

        validateNickname(nickname);

        boolean duplicated = userRepository
            .existsByNicknameAndIdNotAndDeletedAtIsNull(
                nickname,
                user.getId()
            );

        return new NicknameCheckResponse(
            nickname,
            !duplicated
        );
    }

    private void validateNickname(String nickname) {
        if (
            nickname == null ||
            !NICKNAME_PATTERN.matcher(nickname).matches()
        ) {
            throw new BusinessException(
                UserErrorCode.INVALID_NICKNAME
            );
        }
    }

    private void validateUpdateRequest(
        UserUpdateRequest request
    ) {
        if (request != null && request.hasUnknownFields()) {
            throw new BusinessException(
                CommonErrorCode.MALFORMED_JSON
            );
        }

        if (
            request == null ||
            (
                !request.isNicknameProvided() &&
                !request.isProfileImageCodeProvided()
            )
        ) {
            throw new BusinessException(
                UserErrorCode.EMPTY_UPDATE_REQUEST
            );
        }
    }

    private void validateNicknameDuplication(
        String nickname,
        Long userId
    ) {
        if (
            userRepository
                .existsByNicknameAndIdNotAndDeletedAtIsNull(
                    nickname,
                    userId
                )
        ) {
            throw new BusinessException(
                UserErrorCode.NICKNAME_DUPLICATED
            );
        }
    }

    private ProfileImageCode parseProfileImageCode(
        String profileImageCode
    ) {
        if (profileImageCode == null) {
            throw new BusinessException(
                UserErrorCode.INVALID_PROFILE_IMAGE
            );
        }

        try {
            return ProfileImageCode.valueOf(profileImageCode);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                UserErrorCode.INVALID_PROFILE_IMAGE
            );
        }
    }

    private void flushProfileUpdate() {
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (isNicknameUniqueConflict(exception)) {
                throw new BusinessException(
                    UserErrorCode.NICKNAME_DUPLICATED
                );
            }

            throw exception;
        }
    }

    private boolean isNicknameUniqueConflict(
        DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (
                cause instanceof ConstraintViolationException
                    constraintViolation
            ) {
                return NICKNAME_UNIQUE_CONSTRAINT.equals(
                    constraintViolation.getConstraintName()
                );
            }

            cause = cause.getCause();
        }

        return false;
    }

    private UserResponse createUserResponse(User user) {
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
