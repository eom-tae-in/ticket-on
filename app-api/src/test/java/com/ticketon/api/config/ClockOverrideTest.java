package com.ticketon.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.common.MutableClock;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionOverrideException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestContextManager;
import org.springframework.test.context.bean.override.convention.TestBean;

/**
 * Spring 통합 테스트에서 Clock 빈을 고정·이동 가능한 시계로 바꾸는 방법. {@code @SpringBootTest}에서도 슬라이스 테스트에서도 같다.
 * <ol>
 *   <li>{@link MutableClock}을 static 상수로 만든다. 테스트는 이 상수로 시계를 옮긴다.</li>
 *   <li>{@code @TestBean(name = "clock")} 칸을 <b>{@link Clock} 타입</b>으로 두고, 같은 이름의 static 메서드가 그 상수를 돌려주게 한다.</li>
 *   <li>컨텍스트가 테스트 사이에 재사용되므로 시계도 공유된다 — 각 테스트 앞에서 시각을 다시 맞춘다.</li>
 * </ol>
 * 칸 타입을 MutableClock으로 두면 안 된다. 이름으로 바꾸는 {@code @TestBean}은 이름이 같고 칸 타입에 맞는 빈만 바꿀 대상으로 보는데,
 * clock 빈은 {@link ClockConfiguration}의 {@code @Bean} 메서드 반환 타입대로 Clock으로 등록돼 있다. 그래서 바꿀 빈이 없다고 보고
 * 같은 이름의 빈을 새로 등록하려 하고, Spring Boot는 빈 정의 덮어쓰기를 막으므로 컨텍스트가 뜨지 않는다.
 * Boot 없이 {@code @SpringJUnitConfig}로 띄우면 덮어쓰기가 허용돼 MutableClock 칸도 통과해 버리므로, 이 예시는 {@code @SpringBootTest}로
 * 띄운다. 빈은 ClockConfiguration 하나만 올려 DB 없이 돈다.
 */
@SpringBootTest(classes = ClockConfiguration.class, webEnvironment = WebEnvironment.NONE)
@DisplayName("통합 테스트에서 Clock 빈 바꾸기")
class ClockOverrideTest {

    private static final LocalDateTime SESSION_START = LocalDateTime.of(2026, 3, 14, 19, 0);

    /** 테스트가 옮기는 시계. 아래 clock 칸이 Clock 빈을 이것으로 바꾼다. */
    private static final MutableClock MUTABLE_CLOCK = MutableClock.at(SESSION_START);

    @TestBean(name = "clock")
    private Clock clock;

    /** 서비스가 주입받는 것과 같은 Clock 빈. */
    @Autowired
    private Clock injectedClock;

    static Clock clock() {
        return MUTABLE_CLOCK;
    }

    @BeforeEach
    void resetClock() {
        MUTABLE_CLOCK.moveTo(SESSION_START);
    }

    @Test
    @DisplayName("주입되는 Clock이 고정 시계로 바뀐다")
    void injectedClockIsReplaced() {
        assertThat(injectedClock).isSameAs(MUTABLE_CLOCK);
        assertThat(LocalDateTime.now(injectedClock)).isEqualTo(SESSION_START);
    }

    @Test
    @DisplayName("테스트가 시계를 옮기면 주입된 Clock도 옮겨진다")
    void movingClockIsVisibleThroughBean() {
        MUTABLE_CLOCK.advance(Duration.ofMinutes(10));

        assertThat(LocalDateTime.now(injectedClock)).isEqualTo(SESSION_START.plusMinutes(10));
    }

    @Test
    @DisplayName("칸 타입을 MutableClock으로 두면 같은 이름의 빈을 새로 등록하려다 막혀 컨텍스트가 뜨지 않는다")
    void mutableClockTypedFieldCannotReplaceClockBean() {
        TestContextManager testContextManager = new TestContextManager(MutableClockTypedField.class);

        assertThatThrownBy(() -> testContextManager.prepareTestInstance(new MutableClockTypedField()))
                .hasRootCauseInstanceOf(BeanDefinitionOverrideException.class);
    }

    /** 틀린 예 — 위 테스트가 이 설정으로 컨텍스트를 직접 띄워 본다. 테스트 메서드가 없어 JUnit이 따로 돌리지 않는다. */
    @SpringBootTest(classes = ClockConfiguration.class, webEnvironment = WebEnvironment.NONE)
    static class MutableClockTypedField {

        @TestBean(name = "clock")
        private MutableClock clock;

        static MutableClock clock() {
            return MutableClock.at(SESSION_START);
        }
    }
}
