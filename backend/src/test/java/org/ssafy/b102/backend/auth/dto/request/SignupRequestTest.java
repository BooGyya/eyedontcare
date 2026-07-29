package org.ssafy.b102.backend.auth.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SignupRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();
    }

    @Test
    void 올바른_회원가입_요청은_검증에_성공한다() {
        SignupRequest request = new SignupRequest(
            "user@example.com",
            "password123"
        );

        Set<ConstraintViolation<SignupRequest>> violations =
            validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void 이메일_형식이_올바르지_않으면_검증에_실패한다() {
        SignupRequest request = new SignupRequest(
            "invalid-email",
            "password123"
        );

        Set<ConstraintViolation<SignupRequest>> violations =
            validator.validate(request);

        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("올바른 이메일 형식이 아닙니다.");
    }

    @Test
    void 비밀번호에_숫자가_없으면_검증에_실패한다() {
        SignupRequest request = new SignupRequest(
            "user@example.com",
            "password"
        );

        Set<ConstraintViolation<SignupRequest>> violations =
            validator.validate(request);

        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains(
                "비밀번호에는 영문과 숫자가 각각 하나 이상 포함되어야 합니다."
            );
    }

    @Test
    void 비밀번호에_영문이_없으면_검증에_실패한다() {
        SignupRequest request = new SignupRequest(
            "user@example.com",
            "12345678"
        );

        Set<ConstraintViolation<SignupRequest>> violations =
            validator.validate(request);

        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains(
                "비밀번호에는 영문과 숫자가 각각 하나 이상 포함되어야 합니다."
            );
    }

    @Test
    void 비밀번호가_8자보다_짧으면_검증에_실패한다() {
        SignupRequest request = new SignupRequest(
            "user@example.com",
            "pass123"
        );

        Set<ConstraintViolation<SignupRequest>> violations =
            validator.validate(request);

        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("비밀번호는 8자 이상 16자 이하여야 합니다.");
    }

    @Test
    void 비밀번호가_16자보다_길면_검증에_실패한다() {
        SignupRequest request = new SignupRequest(
            "user@example.com",
            "password123456789"
        );

        Set<ConstraintViolation<SignupRequest>> violations =
            validator.validate(request);

        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("비밀번호는 8자 이상 16자 이하여야 합니다.");
    }
}
