package org.ssafy.b102.backend.user.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RandomNicknameGeneratorTest {

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
}
