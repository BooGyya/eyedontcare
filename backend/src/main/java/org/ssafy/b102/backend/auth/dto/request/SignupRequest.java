package org.ssafy.b102.backend.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
    String email,

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(
        min = 8,
        max = 16,
        message = "비밀번호는 8자 이상 16자 이하여야 합니다."
    )
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
        message = "비밀번호에는 영문과 숫자가 각각 하나 이상 포함되어야 합니다."
    )
    String password

) {
}
