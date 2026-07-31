package org.ssafy.b102.backend.user.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.LinkedHashMap;
import java.util.Map;

public class UserUpdateRequest {

    private String nickname;
    private boolean nicknameProvided;
    private String profileImageCode;
    private boolean profileImageCodeProvided;
    private final Map<String, Object> unknownFields =
        new LinkedHashMap<>();

    public String getNickname() {
        return nickname;
    }

    @JsonSetter("nickname")
    public void setNickname(String nickname) {
        this.nickname = nickname;
        this.nicknameProvided = true;
    }

    public boolean isNicknameProvided() {
        return nicknameProvided;
    }

    public String getProfileImageCode() {
        return profileImageCode;
    }

    @JsonSetter("profileImageCode")
    public void setProfileImageCode(String profileImageCode) {
        this.profileImageCode = profileImageCode;
        this.profileImageCodeProvided = true;
    }

    public boolean isProfileImageCodeProvided() {
        return profileImageCodeProvided;
    }

    @JsonAnySetter
    public void addUnknownField(String name, Object value) {
        unknownFields.put(name, value);
    }

    public boolean hasUnknownFields() {
        return !unknownFields.isEmpty();
    }
}
