package org.ssafy.b102.backend.user.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PasswordUpdateRequestTest {

    private Validator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        validator = Validation
            .buildDefaultValidatorFactory()
            .getValidator();
        objectMapper = new ObjectMapper();
    }

    @Test
    void validRequestPassesValidation() {
        assertThat(validate("password123", "newPassword456"))
            .isEmpty();
        assertThat(validate("pass1234", "newPass1"))
            .isEmpty();
        assertThat(validate(
            "password123456",
            "newPassword12345"
        )).isEmpty();
    }

    @Test
    void missingNullAndBlankFieldsFailValidation() {
        assertThat(validate(null, "newPassword456"))
            .isNotEmpty();
        assertThat(validate("password123", null))
            .isNotEmpty();
        assertThat(validate(" ", "newPassword456"))
            .isNotEmpty();
        assertThat(validate("password123", " "))
            .isNotEmpty();
    }

    @Test
    void passwordLengthUsesEightToSixteenPolicy() {
        assertThat(validate("pass123", "newPass1"))
            .isNotEmpty();
        assertThat(validate("password123456789", "newPass1"))
            .isNotEmpty();
        assertThat(validate("password123", "newPas1"))
            .isNotEmpty();
        assertThat(validate(
            "password123",
            "newPassword123456"
        )).isNotEmpty();
    }

    @Test
    void newPasswordRequiresLettersAndNumbers() {
        assertThat(validate("password123", "abcdefgh"))
            .isNotEmpty();
        assertThat(validate("password123", "12345678"))
            .isNotEmpty();
    }

    @Test
    void newPasswordWithInternalWhitespaceFailsValidation() {
        assertThat(validate("password123", "new Pass1"))
            .extracting(ConstraintViolation::getMessage)
            .contains(
                "비밀번호에는 영문과 숫자가 각각 하나 이상 포함되어야 하며 공백은 사용할 수 없습니다."
            );
    }

    @Test
    void currentPasswordWithInternalWhitespaceIsNotRejectedByNewPolicy() {
        assertThat(validate("pass word1", "newPass1"))
            .isEmpty();
    }

    @Test
    void unknownFieldsAreCollectedAndKnownFieldsAreNot() {
        PasswordUpdateRequest known = objectMapper.readValue(
            """
            {
              "currentPassword": "password123",
              "newPassword": "newPassword456"
            }
            """,
            PasswordUpdateRequest.class
        );
        PasswordUpdateRequest unknown = objectMapper.readValue(
            """
            {
              "currentPassword": "password123",
              "newPassword": "newPassword456",
              "email": "other@example.com"
            }
            """,
            PasswordUpdateRequest.class
        );

        assertThat(known.hasUnknownFields()).isFalse();
        assertThat(unknown.hasUnknownFields()).isTrue();
    }

    private Set<ConstraintViolation<PasswordUpdateRequest>>
    validate(String currentPassword, String newPassword) {
        PasswordUpdateRequest request =
            new PasswordUpdateRequest();
        request.setCurrentPassword(currentPassword);
        request.setNewPassword(newPassword);
        return validator.validate(request);
    }
}
