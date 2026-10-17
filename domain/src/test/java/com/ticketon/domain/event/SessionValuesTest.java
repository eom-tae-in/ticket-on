package com.ticketon.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.text.Normalizer;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("회차의 값 — 회차형 시간 · 운영 시간 · 출연 · 가격 조정 (docs/02 \"회차의 시간 정보\" · \"출연 (선택)\", docs/05 \"회차별 가격 조정\")")
class SessionValuesTest {

    @Nested
    @DisplayName("회차형 시간 — 시작 필수, 종료 선택")
    class TimedTime {

        @Test
        @DisplayName("19:00 시작 ~ 21:00 종료 (docs/02 예시)")
        void startAndEnd() {
            TimedSessionTime time = TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0));

            assertThat(time.startTime()).isEqualTo(LocalTime.of(19, 0));
            assertThat(time.end()).contains(LocalTime.of(21, 0));
        }

        @Test
        @DisplayName("종료 시각은 비워 둘 수 있고, 나중에 채운다")
        void endOptional() {
            TimedSessionTime time = TimedSessionTime.startingAt(LocalTime.of(19, 0));

            assertThat(time.end()).isEmpty();
            assertThat(time.withEndTime(LocalTime.of(21, 0))).isEqualTo(TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0)));
        }

        @Test
        @DisplayName("시작은 비울 수 없다")
        void startRequired() {
            assertThatThrownBy(() -> TimedSessionTime.of(null, LocalTime.of(21, 0))).isInstanceOf(NullPointerException.class);
            assertThat(TimedSessionTime.isValid(null, null)).isFalse();
        }

        @Test
        @DisplayName("종료는 시작보다 늦어야 한다 — 같거나 이르면 받지 않는다 (자정을 넘기는 회차는 확인 필요)")
        void endAfterStart() {
            assertThatIllegalArgumentException().isThrownBy(() -> TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(19, 0)));
            assertThatIllegalArgumentException().isThrownBy(() -> TimedSessionTime.of(LocalTime.of(22, 0), LocalTime.of(0, 30)));
            assertThat(TimedSessionTime.isValid(LocalTime.of(19, 0), LocalTime.of(19, 0))).isFalse();
            assertThat(TimedSessionTime.isValid(LocalTime.of(19, 0), LocalTime.of(19, 1))).isTrue();
            assertThat(TimedSessionTime.isValid(LocalTime.of(19, 0), null)).isTrue();
        }

        @Test
        @DisplayName("시각은 분 단위만 받는다 — 19:00:30 시작이나 21:00:00.5 종료는 자르지 않고 받지 않는다 (확인 필요 #41)")
        void wholeMinutesOnly() {
            LocalTime halfMinutePast = LocalTime.of(19, 0, 30);
            LocalTime halfSecondPast = LocalTime.of(21, 0, 0, 500_000_000);

            assertThatIllegalArgumentException().isThrownBy(() -> TimedSessionTime.startingAt(halfMinutePast));
            assertThatIllegalArgumentException().isThrownBy(() -> TimedSessionTime.of(LocalTime.of(19, 0), halfSecondPast));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> TimedSessionTime.startingAt(LocalTime.of(19, 0)).withEndTime(LocalTime.of(21, 0, 1)));
            assertThat(TimedSessionTime.isValid(halfMinutePast, null)).isFalse();
            assertThat(TimedSessionTime.isValid(LocalTime.of(19, 0), halfSecondPast)).isFalse();
            assertThat(MinutePrecision.isWholeMinute(LocalTime.of(19, 0))).isTrue();
            assertThat(MinutePrecision.isWholeMinute(LocalTime.of(19, 0, 0, 1))).isFalse();
        }
    }

    @Nested
    @DisplayName("운영 시간 — 운영 시작~종료 필수, 마지막 입장 선택")
    class Hours {

        @Test
        @DisplayName("10:00 오픈 ~ 18:00 마감, 마지막 입장을 비우면 운영 종료와 같게 처리한다 (docs/02)")
        void lastEntryDefaultsToClose() {
            OperatingHours hours = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0));

            assertThat(hours.lastEntry()).isEmpty();
            assertThat(hours.effectiveLastEntryTime()).isEqualTo(LocalTime.of(18, 0));
        }

        @Test
        @DisplayName("마지막 입장을 적으면 그 시각 — 비운 것과는 다른 값이다")
        void lastEntryGiven() {
            OperatingHours hours = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0), LocalTime.of(17, 0));

            assertThat(hours.effectiveLastEntryTime()).isEqualTo(LocalTime.of(17, 0));
            assertThat(OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0), LocalTime.of(18, 0)))
                    .isNotEqualTo(OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0)));
        }

        @Test
        @DisplayName("운영 시작 · 종료는 비울 수 없다")
        void openAndCloseRequired() {
            assertThatThrownBy(() -> OperatingHours.of(null, LocalTime.of(18, 0))).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> OperatingHours.of(LocalTime.of(10, 0), null)).isInstanceOf(NullPointerException.class);
            assertThat(OperatingHours.isValid(LocalTime.of(10, 0), null, null)).isFalse();
        }

        @Test
        @DisplayName("운영 종료는 운영 시작보다 늦어야 한다 (자정을 넘기는 운영은 확인 필요)")
        void closeAfterOpen() {
            assertThatIllegalArgumentException().isThrownBy(() -> OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(10, 0)));
            assertThatIllegalArgumentException().isThrownBy(() -> OperatingHours.of(LocalTime.of(18, 0), LocalTime.of(2, 0)));
            assertThat(OperatingHours.isValid(LocalTime.of(18, 0), LocalTime.of(2, 0), null)).isFalse();
        }

        @Test
        @DisplayName("마지막 입장은 운영 시작보다 늦고 운영 종료보다 늦지 않아야 한다")
        void lastEntryWithinHours() {
            LocalTime open = LocalTime.of(10, 0);
            LocalTime close = LocalTime.of(18, 0);

            assertThatIllegalArgumentException().isThrownBy(() -> OperatingHours.of(open, close, LocalTime.of(10, 0)));
            assertThatIllegalArgumentException().isThrownBy(() -> OperatingHours.of(open, close, LocalTime.of(9, 0)));
            assertThatIllegalArgumentException().isThrownBy(() -> OperatingHours.of(open, close, LocalTime.of(18, 1)));
            assertThat(OperatingHours.isValid(open, close, LocalTime.of(10, 1))).isTrue();
            assertThat(OperatingHours.isValid(open, close, LocalTime.of(18, 0))).isTrue();
            assertThat(OperatingHours.isValid(open, close, LocalTime.of(18, 1))).isFalse();
        }

        @Test
        @DisplayName("운영 시작 · 종료 · 마지막 입장도 분 단위만 받는다 (확인 필요 #41)")
        void wholeMinutesOnly() {
            LocalTime open = LocalTime.of(10, 0);
            LocalTime close = LocalTime.of(18, 0);

            assertThatIllegalArgumentException().isThrownBy(() -> OperatingHours.of(LocalTime.of(10, 0, 1), close));
            assertThatIllegalArgumentException().isThrownBy(() -> OperatingHours.of(open, LocalTime.of(18, 0, 0, 1_000)));
            assertThatIllegalArgumentException().isThrownBy(() -> OperatingHours.of(open, close, LocalTime.of(17, 30, 30)));
            assertThat(OperatingHours.isValid(LocalTime.of(10, 0, 1), close, null)).isFalse();
            assertThat(OperatingHours.isValid(open, close, LocalTime.of(17, 30, 30))).isFalse();
            assertThat(OperatingHours.isValid(open, close, LocalTime.of(17, 30))).isTrue();
        }
    }

    @Nested
    @DisplayName("출연 — 선택, 자유 입력 한 줄")
    class Performers {

        @Test
        @DisplayName("\"김배우 · 박배우\"를 적은 그대로 갖는다 (docs/02 예시)")
        void freeTextOneLine() {
            assertThat(SessionPerformers.fromInput("  김배우 · 박배우 ")).contains(new SessionPerformers("김배우 · 박배우"));
        }

        @Test
        @DisplayName("적지 않았거나 공백뿐이면 출연이 없다")
        void emptyMeansNone() {
            assertThat(SessionPerformers.fromInput(null)).isEmpty();
            assertThat(SessionPerformers.fromInput("   ")).isEmpty();
            assertThat(SessionPerformers.isAcceptable(null)).isTrue();
            assertThat(SessionPerformers.isAcceptable(" ")).isTrue();
            assertThatIllegalArgumentException().isThrownBy(() -> new SessionPerformers(" "));
        }

        @Test
        @DisplayName("한 줄이라 줄바꿈은 받지 않는다")
        void lineBreakRejected() {
            assertThatIllegalArgumentException().isThrownBy(() -> SessionPerformers.fromInput("김배우\n박배우"));
            assertThatIllegalArgumentException().isThrownBy(() -> SessionPerformers.fromInput("김배우\r박배우"));
            assertThat(SessionPerformers.isAcceptable("김배우\r\n박배우")).isFalse();
        }

        @Test
        @DisplayName("자모를 늘어놓은 한글도 완성형으로 맞춰 같은 값이 된다")
        void normalizedToNfc() {
            String decomposed = Normalizer.normalize("김배우", Normalizer.Form.NFD);

            assertThat(SessionPerformers.fromInput(decomposed)).contains(new SessionPerformers("김배우"));
        }

        @Test
        @DisplayName("100자까지 받고 101자는 받지 않는다 (확인 필요 #36)")
        void maximumLength() {
            String hundred = "가".repeat(100);

            assertThat(SessionPerformers.fromInput(hundred)).isPresent();
            assertThat(SessionPerformers.isAcceptable(hundred + "가")).isFalse();
            assertThatIllegalArgumentException().isThrownBy(() -> SessionPerformers.fromInput(hundred + "가"));
        }
    }

    @Nested
    @DisplayName("회차별 가격 조정 — 금액 ± 또는 퍼센트 ±")
    class PriceAdjustment {

        @Test
        @DisplayName("3/14 조정 없음 · 3/15 +10,000원 · 3/12 프리뷰 -20% (docs/05 예시)")
        void documentExamples() {
            assertThat(SessionPriceAdjustment.none()).isInstanceOf(SessionPriceAdjustment.None.class);
            assertThat(SessionPriceAdjustment.amount(10_000)).isEqualTo(new SessionPriceAdjustment.Amount(10_000));
            assertThat(SessionPriceAdjustment.percent(-20)).isEqualTo(new SessionPriceAdjustment.Rate(-20));
        }

        @Test
        @DisplayName("조정의 종류 — 조정 없음 · 금액 · 퍼센트")
        void kinds() {
            assertThat(SessionPriceAdjustment.none().kind()).isEqualTo(SessionPriceAdjustmentKind.NONE);
            assertThat(SessionPriceAdjustment.amount(10_000).kind()).isEqualTo(SessionPriceAdjustmentKind.AMOUNT);
            assertThat(SessionPriceAdjustment.percent(-20).kind()).isEqualTo(SessionPriceAdjustmentKind.PERCENT);
        }

        @Test
        @DisplayName("빼는 금액과 더하는 퍼센트도 받는다")
        void bothDirections() {
            assertThat(SessionPriceAdjustment.amount(-5_000)).isEqualTo(new SessionPriceAdjustment.Amount(-5_000));
            assertThat(SessionPriceAdjustment.percent(10)).isEqualTo(new SessionPriceAdjustment.Rate(10));
        }

        @Test
        @DisplayName("0원 · 0%는 조정 없음이다")
        void zeroIsNone() {
            assertThat(SessionPriceAdjustment.amount(0)).isEqualTo(SessionPriceAdjustment.none());
            assertThat(SessionPriceAdjustment.percent(0)).isEqualTo(SessionPriceAdjustment.none());
            assertThatIllegalArgumentException().isThrownBy(() -> new SessionPriceAdjustment.Amount(0));
            assertThatIllegalArgumentException().isThrownBy(() -> new SessionPriceAdjustment.Rate(0));
        }

        @Test
        @DisplayName("-100% 이하는 받지 않는다 — 어느 구역이든 0원 이하가 된다")
        void percentAboveMinusHundred() {
            assertThatIllegalArgumentException().isThrownBy(() -> SessionPriceAdjustment.percent(-100));
            assertThatIllegalArgumentException().isThrownBy(() -> SessionPriceAdjustment.percent(-150));
            assertThat(SessionPriceAdjustment.percent(-99)).isEqualTo(new SessionPriceAdjustment.Rate(-99));
            assertThat(SessionPriceAdjustment.isValidPercent(-100)).isFalse();
            assertThat(SessionPriceAdjustment.isValidPercent(-99)).isTrue();
        }
    }
}
