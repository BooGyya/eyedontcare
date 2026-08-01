package org.ssafy.b102.backend.user.util;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class RandomNicknameGenerator {

    private static final String[] ADJECTIVES = {
        "용감한",
        "졸린",
        "신나는",
        "차분한",
        "명랑한",
        "다정한",
        "재빠른",
        "엉뚱한",
        "빛나는",
        "씩씩한"
    };

    private static final String[] ANIMALS = {
        "수달",
        "판다",
        "토끼",
        "고양이",
        "강아지",
        "여우",
        "펭귄",
        "햄스터",
        "다람쥐",
        "알파카"
    };

    private static final int NUMBER_BOUND = 10_000;

    private final RandomGenerator randomGenerator;

    public RandomNicknameGenerator() {
        this(new SecureRandom());
    }

    RandomNicknameGenerator(RandomGenerator randomGenerator) {
        this.randomGenerator = randomGenerator;
    }

    public String generate() {
        String adjective = ADJECTIVES[
            randomGenerator.nextInt(ADJECTIVES.length)
            ];

        String animal = ANIMALS[
            randomGenerator.nextInt(ANIMALS.length)
            ];

        int number = randomGenerator.nextInt(NUMBER_BOUND);

        return adjective + animal + String.format("%04d", number);
    }
}
