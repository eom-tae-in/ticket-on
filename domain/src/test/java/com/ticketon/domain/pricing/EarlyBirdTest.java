package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.policy.Percent;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("조기예매 — 상태와 설정 바꾸기 (docs/05 \"조기예매\")")
class EarlyBirdTest {

    private static final long EVENT_ID = 1L;
    private static final Instant MARCH_1 = korean(2026, 3, 1, 0, 0);
    private static final Instant MARCH_5 = korean(2026, 3, 5, 12, 0);
    private static final Instant MARCH_10 = korean(2026, 3, 10, 0, 0);
    private static final DiscountPeriod MARCH_1_TO_10 = DiscountPeriod.of(MARCH_1, MARCH_10);
    private static final Discount THIRTY_PERCENT = Discount.rate(Percent.of(30));

    private static Instant korean(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    private static EarlyBirdTerms thirtyTickets() {
        return EarlyBirdTerms.limited(THIRTY_PERCENT, MARCH_1_TO_10, EarlyBirdQuantity.of(30), EarlyBirdScope.everything());
    }

    private static EarlyBird earlyBirdWithClaimed(int claimedCount) {
        return EarlyBird.restore(10L, EVENT_ID, thirtyTickets(), claimedCount, EarlyBirdUrgency.NONE);
    }

    @Nested
    @DisplayName("상태 — 수량 소진과 기간 종료 중 먼저 오는 쪽에 끝난다 (docs/05 \"종료 조건\")")
    class Status {

        @Test
        @DisplayName("기간 전 시작 전, 기간 안 진행 중, 끝 순간부터 기간 종료")
        void byPeriod() {
            EarlyBird earlyBird = earlyBirdWithClaimed(0);

            assertThat(earlyBird.status(MARCH_1.minusSeconds(1))).isEqualTo(EarlyBirdStatus.NOT_STARTED);
            assertThat(earlyBird.status(MARCH_1)).isEqualTo(EarlyBirdStatus.OPEN);
            assertThat(earlyBird.status(MARCH_10.minusSeconds(1))).isEqualTo(EarlyBirdStatus.OPEN);
            assertThat(earlyBird.status(MARCH_10)).isEqualTo(EarlyBirdStatus.ENDED);
        }

        @Test
        @DisplayName("30장을 다 잡으면 기간 안이어도 소진 — 29장이면 진행 중")
        void soldOutBeforePeriodEnds() {
            assertThat(earlyBirdWithClaimed(29).status(MARCH_5)).isEqualTo(EarlyBirdStatus.OPEN);
            assertThat(earlyBirdWithClaimed(30).status(MARCH_5)).isEqualTo(EarlyBirdStatus.SOLD_OUT);
            assertThat(earlyBirdWithClaimed(30).status(MARCH_10)).isEqualTo(EarlyBirdStatus.ENDED);
            assertThat(EarlyBirdStatus.SOLD_OUT.isClosed()).isTrue();
            assertThat(EarlyBirdStatus.ENDED.isClosed()).isTrue();
            assertThat(EarlyBirdStatus.OPEN.isClosed()).isFalse();
        }

        @Test
        @DisplayName("기간만 있는 조기예매는 소진되지 않는다 — 기간이 끝날 때 끝난다")
        void periodOnlyNeverSellsOut() {
            EarlyBird earlyBird = EarlyBird.restore(10L, EVENT_ID,
                    EarlyBirdTerms.periodOnly(THIRTY_PERCENT, MARCH_1_TO_10, EarlyBirdScope.everything()), 5_000, EarlyBirdUrgency.NONE);

            assertThat(earlyBird.status(MARCH_5)).isEqualTo(EarlyBirdStatus.OPEN);
            assertThat(earlyBird.status(MARCH_10)).isEqualTo(EarlyBirdStatus.ENDED);
        }
    }

    @Nested
    @DisplayName("만들기 · 되살리기")
    class Creation {

        @Test
        @DisplayName("새 조기예매는 id가 없고 사용 수 0")
        void created() {
            EarlyBird earlyBird = EarlyBird.create(EVENT_ID, thirtyTickets());

            assertThat(earlyBird.id()).isEmpty();
            assertThat(earlyBird.claimedCount()).isZero();
            assertThat(earlyBird.eventId()).isEqualTo(EVENT_ID);
        }

        @Test
        @DisplayName("사용 수가 수량보다 많거나 음수면 되살리지 않는다")
        void restoreRejectsInvalidCount() {
            assertThatIllegalArgumentException().isThrownBy(() -> earlyBirdWithClaimed(31));
            assertThatIllegalArgumentException().isThrownBy(() -> earlyBirdWithClaimed(-1));
        }

        @Test
        @DisplayName("할인 값이 범위 밖이면 설정을 만들지 않는다 — 정률 100% (확인 필요 #49)")
        void termsRejectWholeRate() {
            assertThatThrownBy(() -> EarlyBirdTerms.periodOnly(Discount.rate(Percent.of(100)), MARCH_1_TO_10, EarlyBirdScope.everything()))
                    .isInstanceOfSatisfying(RuleViolationException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(PricingErrorCode.DISCOUNT_OUT_OF_RANGE));
        }
    }

    @Nested
    @DisplayName("설정 바꾸기 — 판매 중에도 바꾸되 끝난 조기예매는 다시 열지 않는다")
    class Reconfigure {

        @Test
        @DisplayName("진행 중이면 할인 · 수량 · 기간 · 범위를 바꾼다")
        void changesWhileOpen() {
            EarlyBird earlyBird = earlyBirdWithClaimed(10);
            EarlyBirdTerms newTerms = EarlyBirdTerms.limited(Discount.rate(Percent.of(20)), MARCH_1_TO_10, EarlyBirdQuantity.of(50),
                    EarlyBirdScope.everything().withoutZone(3L));

            assertThat(earlyBird.reconfigure(newTerms, MARCH_5)).isTrue();
            assertThat(earlyBird.terms()).isEqualTo(newTerms);
            assertThat(earlyBird.claimedCount()).isEqualTo(10);
        }

        @Test
        @DisplayName("같은 설정이면 바뀌지 않는다")
        void sameTerms() {
            assertThat(earlyBirdWithClaimed(10).reconfigure(thirtyTickets(), MARCH_5)).isFalse();
        }

        @Test
        @DisplayName("소진된 조기예매는 수량을 늘려 다시 열 수 없다 (확인 필요 #49)")
        void soldOutCannotReopen() {
            EarlyBird earlyBird = earlyBirdWithClaimed(30);
            EarlyBirdTerms moreTickets = EarlyBirdTerms.limited(THIRTY_PERCENT, MARCH_1_TO_10, EarlyBirdQuantity.of(50), EarlyBirdScope.everything());

            assertThatThrownBy(() -> earlyBird.reconfigure(moreTickets, MARCH_5))
                    .isInstanceOfSatisfying(RuleViolationException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(PricingErrorCode.EARLY_BIRD_CLOSED));
            assertThat(earlyBird.status(MARCH_5)).isEqualTo(EarlyBirdStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("기간이 끝난 조기예매는 기간을 늘려 다시 열 수 없다 (확인 필요 #49)")
        void endedCannotReopen() {
            EarlyBird earlyBird = earlyBirdWithClaimed(10);
            EarlyBirdTerms longer = EarlyBirdTerms.limited(THIRTY_PERCENT, DiscountPeriod.of(MARCH_1, korean(2026, 3, 20, 0, 0)),
                    EarlyBirdQuantity.of(30), EarlyBirdScope.everything());

            assertThatThrownBy(() -> earlyBird.reconfigure(longer, MARCH_10))
                    .isInstanceOfSatisfying(RuleViolationException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(PricingErrorCode.EARLY_BIRD_CLOSED));
        }

        @Test
        @DisplayName("수량은 잡힌 몫 아래로 줄일 수 없다 — 25장 잡혔으면 24장은 막고 25장은 그 순간 소진 (확인 필요 #49)")
        void quantityNotBelowClaimed() {
            EarlyBird earlyBird = earlyBirdWithClaimed(25);

            assertThatThrownBy(() -> earlyBird.reconfigure(
                    EarlyBirdTerms.limited(THIRTY_PERCENT, MARCH_1_TO_10, EarlyBirdQuantity.of(24), EarlyBirdScope.everything()), MARCH_5))
                    .isInstanceOfSatisfying(RuleViolationException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(PricingErrorCode.EARLY_BIRD_QUANTITY_BELOW_CLAIMED));

            earlyBird.reconfigure(EarlyBirdTerms.limited(THIRTY_PERCENT, MARCH_1_TO_10, EarlyBirdQuantity.of(25), EarlyBirdScope.everything()), MARCH_5);
            assertThat(earlyBird.status(MARCH_5)).isEqualTo(EarlyBirdStatus.SOLD_OUT);
        }
    }
}
