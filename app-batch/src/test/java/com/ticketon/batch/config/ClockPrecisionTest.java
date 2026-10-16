package com.ticketon.batch.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.KoreanTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * 서비스 시계의 정밀도 (#207).
 * <p>
 * PostgreSQL {@code timestamptz}는 마이크로초까지 저장한다. 시계가 더 잘게 읽히면(Linux는 나노초) 방금 만든 객체로 응답한 시각과
 * DB에서 다시 읽은 같은 시각이 달라진다. macOS 시계는 원래 마이크로초라 읽은 값만 봐서는 로컬에서 드러나지 않으므로,
 * 끊는 시계인지도 함께 본다. 테스트가 {@code @TestBean}으로 넣는 MutableClock은 이 빈을 통째로 바꾼다(ClockOverrideTest).
 */
@SpringJUnitConfig(ClockConfiguration.class)
@DisplayName("배치 서버의 시계 정밀도")
class ClockPrecisionTest {

    private static final int READ_COUNT = 10_000;
    private static final long NANOS_PER_MICROSECOND = 1_000;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("여러 번 읽어도 시각의 나노초 부분은 늘 1,000의 배수다")
    void readInstantsHaveMicrosecondPrecision() {
        List<Instant> readInstants = Stream.generate(clock::instant).limit(READ_COUNT).toList();

        assertThat(readInstants)
                .allSatisfy(instant -> assertThat(instant.getNano() % NANOS_PER_MICROSECOND).as("%s", instant).isZero());
    }

    @Test
    @DisplayName("운영체제 시계 정밀도와 상관없이 한국 시간대 시스템 시계를 1마이크로초 단위로 끊는다")
    void clockTicksEveryMicrosecond() {
        Clock expected = Clock.tick(Clock.system(KoreanTime.ZONE), Duration.ofNanos(NANOS_PER_MICROSECOND));

        assertThat(clock).isEqualTo(expected);
    }
}
