package org.ssafy.b102.backend.auth.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginRequestTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();
    }

    @Test
    void 올바른_로그인_요청은_검증에_성공한다() {
        LoginRequest request = new LoginRequest(
            " user@example.com ",
            "password123"
        );

        Set<ConstraintViolation<LoginRequest>> violations =
            validator.validate(request);

        assertThat(violations).isEmpty();
        assertThat(request.email())
            .isEqualTo("user@example.com");
    }

    @Test
    void 비밀번호_복잡도는_로그인_요청에서_검증하지_않는다() {
        LoginRequest request = new LoginRequest(
            "user@example.com",
            "abcdefgh"
        );

        Set<ConstraintViolation<LoginRequest>> violations =
            validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void 로그인_비밀번호_내부_공백은_새_비밀번호_정책으로_거절하지_않는다() {
        LoginRequest request = new LoginRequest(
            "user@example.com",
            "pass word1"
        );

        Set<ConstraintViolation<LoginRequest>> violations =
            validator.validate(request);

        assertThat(violations).isEmpty();
        assertThat(request.password()).isEqualTo("pass word1");
    }

    @Test
    void 이메일_형식이_올바르지_않으면_검증에_실패한다() {
        LoginRequest request = new LoginRequest(
            "invalid-email",
            "password123"
        );

        Set<ConstraintViolation<LoginRequest>> violations =
            validator.validate(request);

        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("올바른 이메일 형식이 아닙니다.");
    }

    @Test
    void 비밀번호가_비어_있으면_검증에_실패한다() {
        LoginRequest request = new LoginRequest(
            "user@example.com",
            " "
        );

        Set<ConstraintViolation<LoginRequest>> violations =
            validator.validate(request);

        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("비밀번호는 필수입니다.");
    }

    @Test
    void 비밀번호가_허용_길이를_벗어나면_검증에_실패한다() {
        LoginRequest request = new LoginRequest(
            "user@example.com",
            "password123456789"
        );

        Set<ConstraintViolation<LoginRequest>> violations =
            validator.validate(request);

        assertThat(violations)
            .extracting(ConstraintViolation::getMessage)
            .contains("비밀번호는 8자 이상 16자 이하여야 합니다.");
    }
}
