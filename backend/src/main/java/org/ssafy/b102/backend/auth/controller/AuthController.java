package org.ssafy.b102.backend.auth.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.ssafy.b102.backend.auth.AuthSuccessCode;
import org.ssafy.b102.backend.auth.dto.request.SignupRequest;
import org.ssafy.b102.backend.auth.dto.response.SignupResponse;
import org.ssafy.b102.backend.auth.service.AuthService;
import org.ssafy.b102.backend.global.common.response.ApiResponse;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(
        @Valid @RequestBody SignupRequest request
    ) {
        SignupResponse response = authService.signup(request);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(
                ApiResponse.success(
                    AuthSuccessCode.SIGNUP_SUCCESS,
                    response
                )
            );
    }
}
