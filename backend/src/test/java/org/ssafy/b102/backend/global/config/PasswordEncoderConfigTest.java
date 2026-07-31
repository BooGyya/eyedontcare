package org.ssafy.b102.backend.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderConfigTest {

    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        PasswordEncoderConfig config = new PasswordEncoderConfig();
        passwordEncoder = config.passwordEncoder();
    }

    @Test
    void 비밀번호를_BCrypt로_암호화한다() {
        String rawPassword = "password123";

        String encodedPassword =
            passwordEncoder.encode(rawPassword);

        assertThat(encodedPassword)
            .isNotEqualTo(rawPassword);

        assertThat(encodedPassword)
            .startsWith("$2");
    }

    @Test
    void 원문_비밀번호와_암호화된_비밀번호가_일치하는지_검증한다() {
        String rawPassword = "password123";
        String encodedPassword =
            passwordEncoder.encode(rawPassword);

        boolean matches = passwordEncoder.matches(
            rawPassword,
            encodedPassword
        );

        assertThat(matches).isTrue();
    }

    @Test
    void 잘못된_비밀번호는_일치하지_않는다() {
        String encodedPassword =
            passwordEncoder.encode("password123");

        boolean matches = passwordEncoder.matches(
            "wrongPassword123",
            encodedPassword
        );

        assertThat(matches).isFalse();
    }

    @Test
    void 같은_비밀번호도_매번_다른_해시를_생성한다() {
        String rawPassword = "password123";

        String firstEncoded =
            passwordEncoder.encode(rawPassword);

        String secondEncoded =
            passwordEncoder.encode(rawPassword);

        assertThat(firstEncoded)
            .isNotEqualTo(secondEncoded);

        assertThat(passwordEncoder.matches(
            rawPassword,
            firstEncoded
        )).isTrue();

        assertThat(passwordEncoder.matches(
            rawPassword,
            secondEncoded
        )).isTrue();
    }
}
