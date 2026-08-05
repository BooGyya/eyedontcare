package org.ssafy.b102.backend.global.validation;

public final class EmailPolicy {

    // jakarta @Email은 문법상 유효하기만 하면 통과해 321@321.321 같은 값도 허용한다.
    // 최상위 도메인(TLD)을 알파벳 2자 이상으로 강제해 실사용 가능한 형식만 받는다.
    public static final String PATTERN =
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
    public static final String PATTERN_MESSAGE =
        "올바른 이메일 형식이 아닙니다.";

    private EmailPolicy() {
    }
}
