package org.ssafy.b102.backend.auth.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.auth.AuthSuccessCode;
import org.ssafy.b102.backend.auth.dto.request.LoginRequest;
import org.ssafy.b102.backend.auth.dto.request.ReissueRequest;
import org.ssafy.b102.backend.auth.dto.request.SignupRequest;
import org.ssafy.b102.backend.auth.dto.response.TokenResponse;
import org.ssafy.b102.backend.auth.service.AuthService;
import org.ssafy.b102.backend.global.common.response.ApiResponse;
import org.ssafy.b102.backend.global.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(
        @Valid @RequestBody SignupRequest request
    ) {
        TokenResponse response = authService.signup(request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                ApiResponse.success(
                    AuthSuccessCode.SIGNUP_SUCCESS,
                    response
                )
            );
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
        @Valid @RequestBody LoginRequest request
    ) {
        TokenResponse response = authService.login(request);

        return ResponseEntity.ok(
            ApiResponse.success(
                AuthSuccessCode.LOGIN_SUCCESS,
                response
            )
        );
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenResponse>> reissue(
        @Valid @RequestBody ReissueRequest request
    ) {
        TokenResponse response = authService.reissue(request);

        return ResponseEntity.ok(
            ApiResponse.success(
                AuthSuccessCode.REISSUE_SUCCESS,
                response
            )
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
        @AuthenticationPrincipal
        AuthenticatedUser authenticatedUser
    ) {
        authService.logout(authenticatedUser.userId());

        return ResponseEntity.ok(
            ApiResponse.success(
                AuthSuccessCode.LOGOUT_SUCCESS,
                null
            )
        );
    }
}
