package com.ticketon.batch.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.KoreanTime;
import com.ticketon.common.SecureRandomTokenGenerator;
import com.ticketon.common.TokenGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig({ClockConfiguration.class, TokenGeneratorConfiguration.class})
@DisplayName("배치 서버의 시계·생성기 빈")
class ClockAndTokenGeneratorBeansTest {

    @Autowired
    private Clock clock;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Test
    @DisplayName("Clock 빈은 Asia/Seoul 시간대의 실제 시계다")
    void clockIsSeoulSystemClock() {
        Instant before = Instant.now();

        Instant clockNow = clock.instant();

        assertThat(clock.getZone()).isEqualTo(KoreanTime.ZONE);
        assertThat(clockNow).isBetween(before, Instant.now().plus(Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("TokenGenerator 빈은 SecureRandom 구현이다")
    void tokenGeneratorIsSecureRandom() {
        assertThat(tokenGenerator).isInstanceOf(SecureRandomTokenGenerator.class);
    }
}
