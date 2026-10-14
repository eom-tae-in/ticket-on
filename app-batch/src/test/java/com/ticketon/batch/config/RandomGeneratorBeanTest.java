package com.ticketon.batch.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(RandomGeneratorConfiguration.class)
@DisplayName(" 무작위 수 생성기 빈")
class RandomGeneratorBeanTest {

    @Autowired
    private RandomGenerator randomGenerator;

    @Test
    @DisplayName("RandomGenerator 빈은 여러 스레드에서 함께 써도 안전한 SecureRandom이다")
    void randomGeneratorIsSecureRandom() {
        assertThat(randomGenerator).isInstanceOf(SecureRandom.class);
        assertThat(randomGenerator.nextInt(10)).isBetween(0, 9);
    }
}
