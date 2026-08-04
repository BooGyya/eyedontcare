package org.ssafy.b102.backend.global.validation;

public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 16;
    public static final String PATTERN =
        "(?U)^(?=.*[A-Za-z])(?=.*\\d)\\S+$";
    public static final String REQUIRED_MESSAGE =
        "비밀번호는 필수입니다.";
    public static final String SIZE_MESSAGE =
        "비밀번호는 8자 이상 16자 이하여야 합니다.";
    public static final String PATTERN_MESSAGE =
        "비밀번호에는 영문과 숫자가 각각 하나 이상 포함되어야 하며 공백은 사용할 수 없습니다.";

    private PasswordPolicy() {
    }
}
