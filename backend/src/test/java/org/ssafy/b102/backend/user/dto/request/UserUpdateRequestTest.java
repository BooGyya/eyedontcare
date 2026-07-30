package org.ssafy.b102.backend.user.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class UserUpdateRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void omittedFieldsAreDistinguishedFromExplicitNull() {
        UserUpdateRequest omitted = objectMapper.readValue(
            "{}",
            UserUpdateRequest.class
        );
        UserUpdateRequest explicitNull = objectMapper.readValue(
            """
            {
              "nickname": null,
              "profileImageCode": null
            }
            """,
            UserUpdateRequest.class
        );

        assertThat(omitted.isNicknameProvided()).isFalse();
        assertThat(omitted.isProfileImageCodeProvided()).isFalse();
        assertThat(explicitNull.isNicknameProvided()).isTrue();
        assertThat(explicitNull.isProfileImageCodeProvided())
            .isTrue();
    }

    @Test
    void knownFieldsAreDeserializedWithoutUnknownFields() {
        UserUpdateRequest request = objectMapper.readValue(
            """
            {
              "nickname": "NewName",
              "profileImageCode": "PROFILE_2"
            }
            """,
            UserUpdateRequest.class
        );

        assertThat(request.getNickname()).isEqualTo("NewName");
        assertThat(request.getProfileImageCode())
            .isEqualTo("PROFILE_2");
        assertThat(request.hasUnknownFields()).isFalse();
    }

    @Test
    void unknownFieldsAreCollected() {
        UserUpdateRequest request = objectMapper.readValue(
            """
            {
              "nickname": "NewName",
              "email": "other@example.com"
            }
            """,
            UserUpdateRequest.class
        );

        assertThat(request.isNicknameProvided()).isTrue();
        assertThat(request.hasUnknownFields()).isTrue();
    }
}
