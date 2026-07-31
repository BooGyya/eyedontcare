package org.ssafy.b102.backend.user.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.regex.Pattern;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RandomNicknameGeneratorTest {

    private static final Pattern NICKNAME_PATTERN =
        Pattern.compile("^[가-힣A-Za-z0-9]{2,10}$");

    @Mock
    private RandomGenerator randomGenerator;

    @Test
    void 지정된_형식으로_닉네임을_생성한다() {
        when(randomGenerator.nextInt(10)).thenReturn(
            0,
            1
        );
        when(randomGenerator.nextInt(10_000))
            .thenReturn(931);

        RandomNicknameGenerator generator =
            new RandomNicknameGenerator(randomGenerator);

        String nickname = generator.generate();

        assertThat(nickname).isEqualTo("용감한판다0931");
    }

    @Test
    void 숫자_부분은_항상_네_자리로_생성한다() {
        when(randomGenerator.nextInt(10)).thenReturn(
            1,
            0
        );
        when(randomGenerator.nextInt(10_000))
            .thenReturn(7);

        RandomNicknameGenerator generator =
            new RandomNicknameGenerator(randomGenerator);

        String nickname = generator.generate();

        assertThat(nickname).isEqualTo("졸린수달0007");
    }

    @Test
    void 모든_단어_조합은_닉네임_정책을_만족한다() {
        String[] adjectives = words("ADJECTIVES");
        String[] animals = words("ANIMALS");

        for (String adjective : adjectives) {
            for (String animal : animals) {
                String nickname = adjective + animal + "0000";

                assertThat(nickname)
                    .hasSizeBetween(2, 10)
                    .matches(NICKNAME_PATTERN);
            }
        }
    }

    private static String[] words(String fieldName) {
        try {
            Field field = RandomNicknameGenerator.class
                .getDeclaredField(fieldName);
            field.setAccessible(true);

            return ((String[]) field.get(null)).clone();
        } catch (
            NoSuchFieldException |
            IllegalAccessException exception
        ) {
            throw new IllegalStateException(
                "닉네임 단어 목록을 읽을 수 없습니다.",
                exception
            );
        }
    }
}
