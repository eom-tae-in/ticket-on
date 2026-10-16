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
    @DisplayName("취소 공제율 구간")
    class CancellationFeeTierTest {

        @Test
        @DisplayName("관람일 당일(0일 전)부터 시작할 수 있고, 음수 날은 거부한다")
        void fromDaysBeforeNonNegative() {
            assertThat(CancellationFeeTier.of(0, 90)).isEqualTo(new CancellationFeeTier(0, Percent.of(90)));
            assertThatThrownBy(() -> CancellationFeeTier.of(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("공제율이 없으면 거부한다")
        void missingRateRejected() {
            assertThatThrownBy(() -> new CancellationFeeTier(3, null)).isInstanceOf(NullPointerException.class);
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

    @Nested
    @DisplayName("파일 용량")
    class FileSizeTest {

        @Test
        @DisplayName("1MB는 1,048,576바이트로 센다 — 2MB는 2,097,152바이트")
        void megabytesAreBinary() {
            assertThat(FileSize.ofMegabytes(2).bytes()).isEqualTo(2_097_152L);
            assertThat(FileSize.ofMegabytes(2)).hasToString("2MB");
        }

        @Test
        @DisplayName("상한과 같은 크기는 넘지 않고, 1바이트라도 크면 넘는다")
        void exceededOnlyAboveLimit() {
            FileSize limit = FileSize.ofMegabytes(2);

            assertThat(limit.isExceededBy(2_097_152L)).isFalse();
            assertThat(limit.isExceededBy(2_097_153L)).isTrue();
            assertThat(limit.isExceededBy(0)).isFalse();
        }

        @Test
        @DisplayName("메가바이트로 나누어떨어지지 않으면 킬로바이트나 바이트로 보인다")
        void readableText() {
            assertThat(new FileSize(512 * 1024)).hasToString("512KB");
            assertThat(new FileSize(100)).hasToString("100B");
        }

        @Test
        @DisplayName("0 이하의 용량은 거부한다")
        void positive() {
            assertThatThrownBy(() -> new FileSize(0)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> FileSize.ofMegabytes(-1)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
