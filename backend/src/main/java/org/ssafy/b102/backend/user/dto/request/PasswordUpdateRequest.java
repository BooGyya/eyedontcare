package org.ssafy.b102.backend.user.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import org.ssafy.b102.backend.global.validation.PasswordPolicy;

public class PasswordUpdateRequest {

    @NotBlank(message = PasswordPolicy.REQUIRED_MESSAGE)
    @Size(
        min = PasswordPolicy.MIN_LENGTH,
        max = PasswordPolicy.MAX_LENGTH,
        message = PasswordPolicy.SIZE_MESSAGE
    )
    private String currentPassword;

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
    private String newPassword;

    private final Map<String, Object> unknownFields =
        new LinkedHashMap<>();

    public PasswordUpdateRequest() {
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    @JsonAnySetter
    public void addUnknownField(String name, Object value) {
        unknownFields.put(name, value);
    }

    public boolean hasUnknownFields() {
        return !unknownFields.isEmpty();
    }
}
