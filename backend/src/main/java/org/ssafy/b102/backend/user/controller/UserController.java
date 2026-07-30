package org.ssafy.b102.backend.user.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.global.common.response.ApiResponse;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;
import org.ssafy.b102.backend.user.UserSuccessCode;
import org.ssafy.b102.backend.user.dto.response.UserResponse;
import org.ssafy.b102.backend.user.dto.response.NicknameCheckResponse;
import org.ssafy.b102.backend.user.service.UserService;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserResponse>> getMyInfo(
        @PathVariable Long userId,
        @AuthenticationPrincipal
        AuthenticatedUser authenticatedUser
    ) {
        UserResponse response = userService.getMyInfo(
            userId,
            authenticatedUser.userId()
        );

        return ResponseEntity.ok(
            ApiResponse.success(
                UserSuccessCode.USER_READ_SUCCESS,
                response
            )
        );
    }

    @GetMapping("/nickname/check")
    public ResponseEntity<ApiResponse<NicknameCheckResponse>>
    checkNicknameAvailability(
        @RequestParam String nickname,
        @AuthenticationPrincipal
        AuthenticatedUser authenticatedUser
    ) {
        NicknameCheckResponse response =
            userService.checkNicknameAvailability(
                authenticatedUser.userId(),
                nickname
            );

        return ResponseEntity.ok(
            ApiResponse.success(
                UserSuccessCode.NICKNAME_CHECK_SUCCESS,
                response
            )
        );
    }
}
