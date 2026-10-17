package com.ticketon.domain.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.policy.Percent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("조기예매 몫 — 선점 순간 누적, 어떤 경우에도 돌아오지 않음 (docs/05 \"조기예매\", docs/14 \"조기예매 몫은 돌아오지 않습니다\")")
class EarlyBirdsTest {

    private static final long EVENT_ID = 1L;
    private static final long S_ZONE = 11L;
    private static final long R_ZONE = 12L;
    private static final long THREE_DAY_PASS_ID = 7L;
    private static final TicketProduct SINGLE = TicketProduct.singleSession();
    private static final TicketProduct THREE_DAY_PASS = TicketProduct.bundle(THREE_DAY_PASS_ID);
    private static final Money REGULAR = Money.of(50_000);

    private static final Instant MARCH_1 = korean(2026, 3, 1, 0, 0);
    private static final Instant MARCH_5 = korean(2026, 3, 5, 12, 0);
    private static final Instant MARCH_10 = korean(2026, 3, 10, 0, 0);
    private static final Instant MARCH_20 = korean(2026, 3, 20, 0, 0);
    private static final DiscountPeriod MARCH_1_TO_10 = DiscountPeriod.of(MARCH_1, MARCH_10);

    /** S석 50,000원 → 35,000원 (docs/05 "조기예매가 섞이면") */
    private static final Discount THIRTY_PERCENT = Discount.rate(Percent.of(30));

    private static Instant korean(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    private static Money won(long won) {
        return Money.of(won);
    }

    private static EarlyBird saved(long id, EarlyBirdTerms terms) {
        return EarlyBird.restore(id, EVENT_ID, terms, 0, EarlyBirdUrgency.NONE);
    }

    /** S석 100석에 조기예매 30장 (docs/05 "조기예매 — 재고") */
    private static EarlyBird thirtyTickets() {
        return saved(10L, EarlyBirdTerms.limited(THIRTY_PERCENT, MARCH_1_TO_10, EarlyBirdQuantity.of(30), EarlyBirdScope.everything()));
    }

    private static List<Money> pricesOf(EarlyBirdAllocation allocation) {
        return BookingPrice.calculate(allocation.ticketsBeforeDiscount(), Optional.empty()).tickets().stream().map(TicketPrice::price).toList();
    }

    @Nested
    @DisplayName("사용 수는 선점 순간 늘고 줄지 않는다")
    class ClaimedCountNeverDecreases {

        @Test
        @DisplayName("S석 100석에 조기예매 30장이면 앞선 30명이 할인가, 31번째부터 정가 (docs/05 \"재고\")")
        void firstThirtyGetDiscount() {
            EarlyBird earlyBird = thirtyTickets();
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(earlyBird));

            for (int person = 1; person <= 30; person++) {
                EarlyBirdAllocation allocation = earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 1, MARCH_5);
                assertThat(allocation.grantedCount()).isEqualTo(1);
                assertThat(allocation.needsRegularPriceConfirmation()).isFalse();
            }
            EarlyBirdAllocation thirtyFirst = earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 1, MARCH_5);

            assertThat(thirtyFirst.grantedCount()).isZero();
            assertThat(pricesOf(thirtyFirst)).containsExactly(won(50_000));
            assertThat(earlyBird.claimedCount()).isEqualTo(30);
        }

        @Test
        @DisplayName("30장 중 30장 선점 → 25장 만료돼도 사용 수 30 그대로 — A는 정가, 뒤에 온 B도 정가 (docs/05 \"선점 만료분을 복구하면 선착순이 깨집니다\")")
        void expiredHoldsDoNotReturnShares() {
            EarlyBird earlyBird = thirtyTickets();
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(earlyBird));
            for (int hold = 0; hold < 7; hold++) {
                earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 4, MARCH_5);
            }
            earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 2, MARCH_5);
            assertThat(earlyBird.claimedCount()).isEqualTo(30);

            EarlyBirdAllocation personA = earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 1, MARCH_5);
            // 10분 뒤 25장이 선점 만료돼 좌석은 풀린다 — 조기예매에는 돌려줄 메서드가 없다
            EarlyBirdAllocation personB = earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 1, MARCH_5.plusSeconds(600));

            assertThat(pricesOf(personA)).containsExactly(won(50_000));
            assertThat(pricesOf(personB)).containsExactly(won(50_000));
            assertThat(earlyBird.claimedCount()).isEqualTo(30);
            assertThat(earlyBird.status(MARCH_5.plusSeconds(600))).isEqualTo(EarlyBirdStatus.SOLD_OUT);
        }

        @Test
        @DisplayName("저장소에서 사용 수 30으로 되살려도 소진 그대로 — 결제 후 취소한 사람이 다시 사도 정가")
        void restoredSoldOutStaysSoldOut() {
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(EarlyBird.restore(10L, EVENT_ID, thirtyTickets().terms(), 30, EarlyBirdUrgency.NONE)));

            assertThat(earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 1, MARCH_5).grantedCount()).isZero();
        }
    }

    @Nested
    @DisplayName("31번째 사람 — 몫이 없으면 정가로 진행할지 확인 필요 (docs/05 \"31번째 사람\")")
    class ThirtyFirstPerson {

        @Test
        @DisplayName("몫이 없으면 확인 필요 — 조용히 가격만 올리지 않는다")
        void noShareNeedsConfirmation() {
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(EarlyBird.restore(10L, EVENT_ID, thirtyTickets().terms(), 30, EarlyBirdUrgency.NONE)));

            EarlyBirdAllocation allocation = earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 1, MARCH_5);

            assertThat(allocation.applicable()).isTrue();
            assertThat(allocation.shortfall()).isEqualTo(1);
            assertThat(allocation.needsRegularPriceConfirmation()).isTrue();
            assertThat(allocation.ticketsBeforeDiscount()).containsExactly(TicketBeforeDiscount.regular(REGULAR));
        }

        @Test
        @DisplayName("몫이 2장만 남았는데 4매 → 35,000 · 35,000 · 50,000 · 50,000원, 합계 17만원, 모자란 2장은 확인 필요 (docs/05 \"조기예매가 섞이면\")")
        void twoOfFour() {
            EarlyBird earlyBird = EarlyBird.restore(10L, EVENT_ID, thirtyTickets().terms(), 28, EarlyBirdUrgency.NONE);
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(earlyBird));

            EarlyBirdAllocation allocation = earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 4, MARCH_5);
            BookingPrice bookingPrice = BookingPrice.calculate(allocation.ticketsBeforeDiscount(), Optional.empty());

            assertThat(allocation.grantedCount()).isEqualTo(2);
            assertThat(allocation.shortfall()).isEqualTo(2);
            assertThat(allocation.needsRegularPriceConfirmation()).isTrue();
            assertThat(allocation.shares()).containsExactly(new EarlyBirdShare(10L, THIRTY_PERCENT, 2));
            assertThat(pricesOf(allocation)).containsExactly(won(35_000), won(35_000), won(50_000), won(50_000));
            assertThat(bookingPrice.total()).isEqualTo(won(170_000));
            assertThat(earlyBird.claimedCount()).isEqualTo(30);
        }

        @Test
        @DisplayName("기간 밖이면 닿는 조기예매가 없다 — 정가이고 묻지 않는다")
        void outsidePeriodDoesNotAsk() {
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(thirtyTickets()));

            EarlyBirdAllocation beforeStart = earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 2, MARCH_1.minusSeconds(1));
            EarlyBirdAllocation afterEnd = earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 2, MARCH_10);

            assertThat(beforeStart.applicable()).isFalse();
            assertThat(beforeStart.needsRegularPriceConfirmation()).isFalse();
            assertThat(afterEnd.applicable()).isFalse();
            assertThat(afterEnd.needsRegularPriceConfirmation()).isFalse();
            assertThat(pricesOf(afterEnd)).containsExactly(won(50_000), won(50_000));
            assertThat(earlyBirds.all().getFirst().claimedCount()).isZero();
        }

        @Test
        @DisplayName("몫이 넉넉하면 묻지 않는다")
        void enoughShares() {
            EarlyBirdAllocation allocation = EarlyBirds.of(List.of(thirtyTickets())).claim(S_ZONE, SINGLE, REGULAR, 4, MARCH_5);

            assertThat(allocation.grantedCount()).isEqualTo(4);
            assertThat(allocation.needsRegularPriceConfirmation()).isFalse();
            assertThat(pricesOf(allocation)).containsOnly(won(35_000));
        }
    }

    @Nested
    @DisplayName("기간만 있는 조기예매 — 수량 없이 기간이 끝날 때까지 (docs/05 \"종료 조건\")")
    class PeriodOnly {

        @Test
        @DisplayName("몇 장이든 몫을 준다")
        void alwaysGrants() {
            EarlyBird earlyBird = saved(10L, EarlyBirdTerms.periodOnly(THIRTY_PERCENT, MARCH_1_TO_10, EarlyBirdScope.everything()));
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(earlyBird));
            for (int hold = 0; hold < 100; hold++) {
                earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 4, MARCH_5);
            }

            assertThat(earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 4, MARCH_5).grantedCount()).isEqualTo(4);
            assertThat(earlyBird.claimedCount()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("적용 범위 — 기본 전체 구역 · 전체 상품, 구역별 · 상품별 끔 (docs/05 \"적용 범위\")")
    class Scope {

        @Test
        @DisplayName("기본값은 모든 구역 · 모든 상품에 닿는다")
        void everythingByDefault() {
            assertThat(EarlyBirdScope.everything().covers(S_ZONE, SINGLE)).isTrue();
            assertThat(EarlyBirdScope.everything().covers(R_ZONE, THREE_DAY_PASS)).isTrue();
        }

        @Test
        @DisplayName("끈 구역에는 닿지 않는다 — 정가이고 묻지 않는다")
        void zoneTurnedOff() {
            EarlyBird earlyBird = saved(10L, EarlyBirdTerms.limited(THIRTY_PERCENT, MARCH_1_TO_10, EarlyBirdQuantity.of(30),
                    EarlyBirdScope.everything().withoutZone(R_ZONE)));
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(earlyBird));

            EarlyBirdAllocation rZone = earlyBirds.claim(R_ZONE, SINGLE, won(40_000), 2, MARCH_5);
            EarlyBirdAllocation sZone = earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 2, MARCH_5);

            assertThat(rZone.applicable()).isFalse();
            assertThat(rZone.needsRegularPriceConfirmation()).isFalse();
            assertThat(sZone.grantedCount()).isEqualTo(2);
            assertThat(earlyBird.claimedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("얼리버드 3일권만 — 1일권을 끄면 3일권 94,500원 → 10% → 85,000원, 1일권은 정가 (docs/05 \"조기예매·쿠폰과 함께 씁니다\")")
        void threeDayPassOnly() {
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(saved(10L, EarlyBirdTerms.limited(Discount.rate(Percent.of(10)), MARCH_1_TO_10,
                    EarlyBirdQuantity.of(100), EarlyBirdScope.everything().withoutProduct(SINGLE)))));

            EarlyBirdAllocation threeDayPass = earlyBirds.claim(S_ZONE, THREE_DAY_PASS, won(94_500), 1, MARCH_5);
            EarlyBirdAllocation oneDayPass = earlyBirds.claim(S_ZONE, SINGLE, won(35_000), 1, MARCH_5);

            assertThat(pricesOf(threeDayPass)).containsExactly(won(85_000));
            assertThat(oneDayPass.applicable()).isFalse();
            assertThat(pricesOf(oneDayPass)).containsExactly(won(35_000));
        }

        @Test
        @DisplayName("묶음권 1장은 몫 1개 — 3일권 4장은 4개 (docs/05 \"티켓 수 기준\")")
        void bundleTicketCountsOnce() {
            EarlyBird earlyBird = thirtyTickets();

            EarlyBirds.of(List.of(earlyBird)).claim(S_ZONE, THREE_DAY_PASS, won(94_500), 4, MARCH_5);

            assertThat(earlyBird.claimedCount()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("행사당 여러 개 — 1차 · 2차 얼리버드 (docs/05 \"종료 조건\")")
    class Several {

        private final EarlyBird first = saved(10L,
                EarlyBirdTerms.limited(THIRTY_PERCENT, MARCH_1_TO_10, EarlyBirdQuantity.of(30), EarlyBirdScope.everything()));
        private final EarlyBird second = saved(20L, EarlyBirdTerms.limited(Discount.rate(Percent.of(20)), DiscountPeriod.of(MARCH_10, MARCH_20),
                EarlyBirdQuantity.of(50), EarlyBirdScope.everything()));

        @Test
        @DisplayName("1차가 끝나는 순간 2차가 시작하면 겹치지 않아 경고가 없다")
        void consecutiveNoWarning() {
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(first));

            assertThat(earlyBirds.overlapping(second.terms().period())).isEmpty();
        }

        @Test
        @DisplayName("기간이 겹치면 저장할 때 경고 정보 — 겹친 조기예매를 준다 (막지는 않는다)")
        void overlapWarning() {
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(first, second));
            DiscountPeriod overlappingFirst = DiscountPeriod.of(korean(2026, 3, 8, 0, 0), korean(2026, 3, 9, 0, 0));

            assertThat(earlyBirds.overlapping(overlappingFirst)).containsExactly(first);
            assertThat(earlyBirds.overlapping(DiscountPeriod.of(MARCH_5, MARCH_20))).containsExactly(first, second);
        }

        @Test
        @DisplayName("기간을 바꿀 때는 자기 자신을 빼고 본다")
        void overlapExcludesItself() {
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(first, second));

            assertThat(earlyBirds.overlappingOthers(10L, DiscountPeriod.of(MARCH_1, MARCH_5))).isEmpty();
            assertThat(earlyBirds.overlappingOthers(10L, DiscountPeriod.of(MARCH_1, korean(2026, 3, 11, 0, 0)))).containsExactly(second);
        }

        @Test
        @DisplayName("1차 기간에는 1차, 2차 기간에는 2차 — 50,000원 → 35,000원 / 40,000원")
        void eachInItsPeriod() {
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(first, second));

            assertThat(pricesOf(earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 1, MARCH_5))).containsExactly(won(35_000));
            assertThat(pricesOf(earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 1, korean(2026, 3, 15, 0, 0)))).containsExactly(won(40_000));
            assertThat(first.claimedCount()).isEqualTo(1);
            assertThat(second.claimedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("겹친 기간에는 유리한 쪽부터, 모자라면 다음 것에서 — 30% 2장 남음 + 20% → 35,000 · 35,000 · 40,000 · 40,000원 (확인 필요 #49)")
        void overlappingFavorableFirstThenNext() {
            EarlyBird almostGone = EarlyBird.restore(10L, EVENT_ID, first.terms(), 28, EarlyBirdUrgency.NONE);
            EarlyBird overlapping = saved(20L, EarlyBirdTerms.limited(Discount.rate(Percent.of(20)), DiscountPeriod.of(MARCH_5, MARCH_20),
                    EarlyBirdQuantity.of(50), EarlyBirdScope.everything()));
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(overlapping, almostGone));

            EarlyBirdAllocation allocation = earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 4, MARCH_5);

            assertThat(allocation.shares()).containsExactly(
                    new EarlyBirdShare(10L, THIRTY_PERCENT, 2), new EarlyBirdShare(20L, Discount.rate(Percent.of(20)), 2));
            assertThat(allocation.needsRegularPriceConfirmation()).isFalse();
            assertThat(pricesOf(allocation)).containsExactly(won(35_000), won(35_000), won(40_000), won(40_000));
        }

        @Test
        @DisplayName("유리한 쪽은 그 기본 가격으로 본다 — 20,000원에는 정액 5,000원이 20%(4,000원)보다 유리하다")
        void favorableDependsOnBasePrice() {
            EarlyBird fixed = saved(10L, EarlyBirdTerms.periodOnly(Discount.fixedAmount(won(5_000)), MARCH_1_TO_10, EarlyBirdScope.everything()));
            EarlyBird rate = saved(20L, EarlyBirdTerms.periodOnly(Discount.rate(Percent.of(20)), MARCH_1_TO_10, EarlyBirdScope.everything()));
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(rate, fixed));

            assertThat(pricesOf(earlyBirds.claim(S_ZONE, SINGLE, won(20_000), 1, MARCH_5))).containsExactly(won(15_000));
            assertThat(pricesOf(earlyBirds.claim(S_ZONE, SINGLE, REGULAR, 1, MARCH_5))).containsExactly(won(40_000));
        }

        @Test
        @DisplayName("얼리버드로 1일권 30,000원, 3일권 85,000원 — 1일권만 켠 정액 5,000원과 3일권만 켠 10% 둘로 만든다 (docs/15 이도현, 기간이 같아 경고는 뜸)")
        void festivalEarlyBirdPerProduct() {
            EarlyBird oneDayPass = saved(10L, EarlyBirdTerms.periodOnly(Discount.fixedAmount(won(5_000)), MARCH_1_TO_10,
                    EarlyBirdScope.everything().withoutProduct(THREE_DAY_PASS)));
            EarlyBird threeDayPass = saved(20L, EarlyBirdTerms.periodOnly(Discount.rate(Percent.of(10)), MARCH_1_TO_10,
                    EarlyBirdScope.everything().withoutProduct(SINGLE)));
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(oneDayPass, threeDayPass));

            assertThat(pricesOf(earlyBirds.claim(S_ZONE, SINGLE, won(35_000), 1, MARCH_5))).containsExactly(won(30_000));
            assertThat(pricesOf(earlyBirds.claim(S_ZONE, THREE_DAY_PASS, won(94_500), 1, MARCH_5))).containsExactly(won(85_000));
            assertThat(EarlyBirds.of(List.of(oneDayPass)).overlapping(threeDayPass.terms().period())).containsExactly(oneDayPass);
        }

        @Test
        @DisplayName("다른 행사의 조기예매는 묶지 않는다")
        void sameEventOnly() {
            EarlyBird otherEvent = EarlyBird.restore(30L, 2L, first.terms(), 0, EarlyBirdUrgency.NONE);

            assertThatIllegalArgumentException().isThrownBy(() -> EarlyBirds.of(List.of(first, otherEvent)));
        }
    }

    @Test
    @DisplayName("저장 전 조기예매에서는 몫을 잡지 않는다")
    void unsavedCannotBeClaimed() {
        EarlyBird unsaved = EarlyBird.create(EVENT_ID, thirtyTickets().terms());

        assertThatIllegalStateException().isThrownBy(() -> EarlyBirds.of(List.of(unsaved)).claim(S_ZONE, SINGLE, REGULAR, 1, MARCH_5));
        assertThat(unsaved.claimedCount()).isZero();
    }
}
