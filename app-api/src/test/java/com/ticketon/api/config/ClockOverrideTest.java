package com.ticketon.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.common.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Spring 통합 테스트에서 Clock 빈을 고정·이동 가능한 시계로 바꾸는 방법.
 * <p>
 * {@code @TestBean(name = "clock")}이 {@link ClockConfiguration}의 clock 빈을 같은 이름의 static 메서드가 만든
 * {@link MutableClock}으로 바꾼다. {@code @SpringBootTest}에서도 똑같이 쓴다.
 * 컨텍스트가 테스트 사이에 재사용되므로 시계도 공유된다 — 각 테스트 앞에서 시각을 다시 맞춘다.
 */
@SpringJUnitConfig(ClockConfiguration.class)
@DisplayName("통합 테스트에서 Clock 빈 바꾸기")
class ClockOverrideTest {

    private static final LocalDateTime SESSION_START = LocalDateTime.of(2026, 3, 14, 19, 0);

    @TestBean(name = "clock")
    private MutableClock clock;

    /** 서비스가 주입받는 것과 같은 Clock 빈. */
    @Autowired
    private Clock injectedClock;

    static MutableClock clock() {
        return MutableClock.at(SESSION_START);
    }

    @BeforeEach
    void resetClock() {
        clock.moveTo(SESSION_START);
    }

    @Test
    @DisplayName("주입되는 Clock이 고정 시계로 바뀐다")
    void injectedClockIsReplaced() {
        assertThat(injectedClock).isSameAs(clock);
        assertThat(LocalDateTime.now(injectedClock)).isEqualTo(SESSION_START);
    }

    @Test
    @DisplayName("테스트가 시계를 옮기면 주입된 Clock도 옮겨진다")
    void movingClockIsVisibleThroughBean() {
        clock.advance(Duration.ofMinutes(10));

        assertThat(LocalDateTime.now(injectedClock)).isEqualTo(SESSION_START.plusMinutes(10));
    }
}
