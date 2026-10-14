package com.ticketon.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("정책 숫자의 값 객체")
class PolicyValuesTest {

    @Nested
    @DisplayName("백분율")
    class PercentTest {

        @Test
        @DisplayName("0과 100은 허용한다")
        void boundariesAllowed() {
            assertThat(Percent.of(0).value()).isZero();
            assertThat(Percent.of(100).value()).isEqualTo(100);
        }

        @Test
        @DisplayName("0 미만이나 100 초과는 거부한다")
        void outOfRangeRejected() {
            assertThatThrownBy(() -> Percent.of(-1)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Percent.of(101)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("같은 숫자면 같은 값이고, 문자열은 '10%'처럼 보인다")
        void valueEquality() {
            assertThat(Percent.of(10)).isEqualTo(new Percent(10));
            assertThat(Percent.of(10)).hasToString("10%");
        }
    }

    @Nested
    @DisplayName("개수·비율 기준선")
    class QuantityThresholdTest {

        @Test
        @DisplayName("음수 개수는 거부한다")
        void negativeCountRejected() {
            assertThatThrownBy(() -> new QuantityThreshold(-1, Percent.of(10)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("비율이 없으면 거부한다")
        void missingPercentRejected() {
            assertThatThrownBy(() -> new QuantityThreshold(10, null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("가로세로 비율")
    class AspectRatioTest {

        @Test
        @DisplayName("3:4는 세로형, 4:3은 가로형이다")
        void portraitOrLandscape() {
            assertThat(new AspectRatio(3, 4).isPortrait()).isTrue();
            assertThat(new AspectRatio(4, 3).isPortrait()).isFalse();
            assertThat(new AspectRatio(3, 4)).hasToString("3:4");
        }

        @Test
        @DisplayName("0 이하의 변은 거부한다")
        void nonPositiveSideRejected() {
            assertThatThrownBy(() -> new AspectRatio(0, 4)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new AspectRatio(3, -4)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("영업일 수")
    class BusinessDaysTest {

        @Test
        @DisplayName("0은 허용하고 음수는 거부한다")
        void nonNegative() {
            assertThat(BusinessDays.of(0).count()).isZero();
            assertThatThrownBy(() -> BusinessDays.of(-1)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
