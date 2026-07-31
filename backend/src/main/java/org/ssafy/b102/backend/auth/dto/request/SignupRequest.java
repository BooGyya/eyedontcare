package org.ssafy.b102.backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.ssafy.b102.backend.global.validation.PasswordPolicy;

public record SignupRequest(

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
    String email,

    @NotBlank(message = PasswordPolicy.REQUIRED_MESSAGE)
    @Size(
        min = PasswordPolicy.MIN_LENGTH,
        max = PasswordPolicy.MAX_LENGTH,
        message = PasswordPolicy.SIZE_MESSAGE
    )
    @Pattern(
        regexp = PasswordPolicy.PATTERN,
        message = PasswordPolicy.PATTERN_MESSAGE
    )
    String password

) {
}
