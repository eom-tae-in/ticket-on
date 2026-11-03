package com.ticketon.domain.pricing;

import static com.ticketon.domain.pricing.BundleProductFixtures.CHILDREN;
import static com.ticketon.domain.pricing.BundleProductFixtures.DAY_MAY_3;
import static com.ticketon.domain.pricing.BundleProductFixtures.DAY_MAY_4;
import static com.ticketon.domain.pricing.BundleProductFixtures.DAY_MAY_5;
import static com.ticketon.domain.pricing.BundleProductFixtures.EXHIBITION_EVENT_ID;
import static com.ticketon.domain.pricing.BundleProductFixtures.EXHIBITION_ZONE;
import static com.ticketon.domain.pricing.BundleProductFixtures.FESTIVAL_EVENT_ID;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_3;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_4;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_5;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_6;
import static com.ticketon.domain.pricing.BundleProductFixtures.NIGHT_MAY_4;
import static com.ticketon.domain.pricing.BundleProductFixtures.SESSION_MAY_3;
import static com.ticketon.domain.pricing.BundleProductFixtures.SESSION_MAY_4;
import static com.ticketon.domain.pricing.BundleProductFixtures.SESSION_MAY_5;
import static com.ticketon.domain.pricing.BundleProductFixtures.SESSION_MAY_6;
import static com.ticketon.domain.pricing.BundleProductFixtures.SESSION_MAY_7;
import static com.ticketon.domain.pricing.BundleProductFixtures.STANDING_A;
import static com.ticketon.domain.pricing.BundleProductFixtures.STANDING_B;
import static com.ticketon.domain.pricing.BundleProductFixtures.exhibitionFloorPlan;
import static com.ticketon.domain.pricing.BundleProductFixtures.exhibitionSchedule;
import static com.ticketon.domain.pricing.BundleProductFixtures.exhibitionScheduleWithDayMay4Cancelled;
import static com.ticketon.domain.pricing.BundleProductFixtures.exhibitionThreeDayPass;
import static com.ticketon.domain.pricing.BundleProductFixtures.festivalFloorPlan;
import static com.ticketon.domain.pricing.BundleProductFixtures.festivalSchedule;
import static com.ticketon.domain.pricing.BundleProductFixtures.festivalThreeDayPass;
import static com.ticketon.domain.pricing.BundleProductFixtures.terms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.policy.Percent;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("묶음권 상품 가격 — 목록의 시작 가격과 회차를 고른 확정 가격 (docs/05 \"조합은 자동 생성하지 않습니다\")")
class BundleProductPriceTest {

    private static final Set<Long> ALL_FESTIVAL_SESSIONS = Set.of(SESSION_MAY_3, SESSION_MAY_4, SESSION_MAY_5, SESSION_MAY_6, SESSION_MAY_7);
    private static final Set<Long> ALL_EXHIBITION_SESSIONS = Set.of(DAY_MAY_3, DAY_MAY_4, NIGHT_MAY_4, DAY_MAY_5);

    private static BundleProduct festivalProduct(BundleProductTerms terms) {
        return BundleProduct.restore(11L, FESTIVAL_EVENT_ID, terms);
    }

    private static BundleProduct exhibitionPass() {
        return BundleProduct.restore(21L, EXHIBITION_EVENT_ID, exhibitionThreeDayPass());
    }

    private static void assertRuleViolation(ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(RuleViolationException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }

    @Nested
    @DisplayName("회차를 다 고르면 확정된다 — 관객이 고른 회차의 가격으로 계산")
    class ChosenSessions {

        @Test
        @DisplayName("5/3 (금) 40,000 + 5/4 (토) 50,000 + 5/5 (일) 50,000 = 140,000원 → 3일권 7% → 130,200원")
        void festivalPassPrice() {
            BundleProduct product = festivalProduct(festivalThreeDayPass());
            List<Long> chosen = List.of(SESSION_MAY_3, SESSION_MAY_4, SESSION_MAY_5);

            BundleDayPrices dayPrices = product.dayPricesFor(STANDING_A, chosen, festivalSchedule(), festivalFloorPlan());

            assertThat(dayPrices.prices()).extracting(SessionPrice::amount)
                    .containsExactly(Money.of(40_000), Money.of(50_000), Money.of(50_000));
            assertThat(dayPrices.total()).isEqualTo(Money.of(140_000));
            assertThat(product.priceFor(STANDING_A, chosen, festivalSchedule(), festivalFloorPlan())).isEqualTo(Money.of(130_200));
        }

        @Test
        @DisplayName("전시 3일권 — 5/4 낮을 고르면 그날 15,000원, 야간을 고르면 20,000원(회차별 조정 +5,000원)")
        void dayOrNightOnMay4() {
            BundleProduct product = exhibitionPass();

            BundleDayPrices day = product.dayPricesFor(EXHIBITION_ZONE, List.of(DAY_MAY_3, DAY_MAY_4, DAY_MAY_5),
                    exhibitionSchedule(), exhibitionFloorPlan());
            BundleDayPrices night = product.dayPricesFor(EXHIBITION_ZONE, List.of(DAY_MAY_3, NIGHT_MAY_4, DAY_MAY_5),
                    exhibitionSchedule(), exhibitionFloorPlan());

            assertThat(day.prices()).extracting(SessionPrice::amount).containsExactly(Money.of(15_000), Money.of(15_000), Money.of(15_000));
            assertThat(night.prices()).extracting(SessionPrice::amount).containsExactly(Money.of(15_000), Money.of(20_000), Money.of(15_000));
            assertThat(product.priceFor(EXHIBITION_ZONE, List.of(DAY_MAY_3, NIGHT_MAY_4, DAY_MAY_5), exhibitionSchedule(),
                    exhibitionFloorPlan())).isEqualTo(Money.of(46_500));
        }

        @Test
        @DisplayName("고른 차례와 상관없이 날짜 순서로 담는다 — 그날 몫을 날짜별 가격으로 나눌 수 있게")
        void orderedByDate() {
            BundleDayPrices dayPrices = exhibitionPass().dayPricesFor(EXHIBITION_ZONE, List.of(DAY_MAY_5, NIGHT_MAY_4, DAY_MAY_3),
                    exhibitionSchedule(), exhibitionFloorPlan());

            assertThat(dayPrices.prices()).extracting(SessionPrice::amount).containsExactly(Money.of(15_000), Money.of(20_000), Money.of(15_000));
        }

        @Test
        @DisplayName("이 묶음권을 팔지 않는 구역은 받지 않는다")
        void zoneNotIncluded() {
            assertRuleViolation(() -> festivalProduct(festivalThreeDayPass()).priceFor(STANDING_B,
                    List.of(SESSION_MAY_3, SESSION_MAY_4, SESSION_MAY_5), festivalSchedule(), festivalFloorPlan()),
                    PricingErrorCode.BUNDLE_ZONE_NOT_INCLUDED);
        }

        @Test
        @DisplayName("날짜마다 회차 하나씩이 아니면 받지 않는다 — 빠진 날짜, 한 날짜에 둘, 묶지 않은 날짜")
        void sessionsMustMatchDates() {
            BundleProduct festival = festivalProduct(festivalThreeDayPass());
            BundleProduct exhibition = exhibitionPass();

            assertRuleViolation(() -> festival.priceFor(STANDING_A, List.of(SESSION_MAY_3, SESSION_MAY_4), festivalSchedule(),
                    festivalFloorPlan()), PricingErrorCode.BUNDLE_SESSIONS_MISMATCH);
            assertRuleViolation(() -> festival.priceFor(STANDING_A, List.of(SESSION_MAY_3, SESSION_MAY_4, SESSION_MAY_6),
                    festivalSchedule(), festivalFloorPlan()), PricingErrorCode.BUNDLE_SESSIONS_MISMATCH);
            assertRuleViolation(() -> festival.priceFor(STANDING_A, List.of(SESSION_MAY_3, SESSION_MAY_4, SESSION_MAY_5, SESSION_MAY_6),
                    festivalSchedule(), festivalFloorPlan()), PricingErrorCode.BUNDLE_SESSIONS_MISMATCH);
            assertRuleViolation(() -> festival.priceFor(STANDING_A, List.of(SESSION_MAY_3, SESSION_MAY_3, SESSION_MAY_5),
                    festivalSchedule(), festivalFloorPlan()), PricingErrorCode.BUNDLE_SESSIONS_MISMATCH);
            assertRuleViolation(() -> exhibition.priceFor(EXHIBITION_ZONE, List.of(DAY_MAY_3, DAY_MAY_4, NIGHT_MAY_4),
                    exhibitionSchedule(), exhibitionFloorPlan()), PricingErrorCode.BUNDLE_SESSIONS_MISMATCH);
        }

        @Test
        @DisplayName("일정에 없는 회차는 찾을 수 없다")
        void unknownSession() {
            assertThatThrownBy(() -> festivalProduct(festivalThreeDayPass()).priceFor(STANDING_A, List.of(SESSION_MAY_3, SESSION_MAY_4, 999L),
                    festivalSchedule(), festivalFloorPlan()))
                    .isInstanceOfSatisfying(NotFoundException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(EventErrorCode.SESSION_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("상품 목록의 시작 가격 — 가장 싼 회차 조합 기준 \"3일권 40,000원~\"")
    class StartingPrice {

        @Test
        @DisplayName("전시 3일권 7% — 날짜마다 가장 싼 회차 15,000 + 15,000 + 15,000 = 45,000원 → 41,800원~")
        void cheapestSessionPerDate() {
            assertThat(exhibitionPass().startingPrice(exhibitionSchedule(), exhibitionFloorPlan(), ALL_EXHIBITION_SESSIONS))
                    .contains(Money.of(41_800));
        }

        @Test
        @DisplayName("페스티벌 3일 통권 — 날짜당 회차 하나라 조합도 하나, 130,200원")
        void singleCombination() {
            assertThat(festivalProduct(festivalThreeDayPass()).startingPrice(festivalSchedule(), festivalFloorPlan(), ALL_FESTIVAL_SESSIONS))
                    .contains(Money.of(130_200));
        }

        @Test
        @DisplayName("구역이 여럿이면 가장 싼 구역 — 스탠딩A 130,200원, 스탠딩B 30,000 + 40,000 + 40,000 = 110,000원 → 102,300원")
        void cheapestZone() {
            BundleProduct product = festivalProduct(terms(Set.of(MAY_3, MAY_4, MAY_5), Set.of(STANDING_A, STANDING_B), 7));

            assertThat(product.startingPrice(festivalSchedule(), festivalFloorPlan(), ALL_FESTIVAL_SESSIONS)).contains(Money.of(102_300));
        }

        @Test
        @DisplayName("예매할 수 있는 회차만 본다 — 5/4 낮의 예매가 마감되면 야간으로 46,500원~")
        void onlyBookableSessions() {
            Set<Long> withoutDayMay4 = Set.of(DAY_MAY_3, NIGHT_MAY_4, DAY_MAY_5);

            assertThat(exhibitionPass().startingPrice(exhibitionSchedule(), exhibitionFloorPlan(), withoutDayMay4)).contains(Money.of(46_500));
        }

        @Test
        @DisplayName("취소된 회차는 보지 않는다")
        void cancelledSessionIgnored() {
            assertThat(exhibitionPass().startingPrice(exhibitionScheduleWithDayMay4Cancelled(), exhibitionFloorPlan(), ALL_EXHIBITION_SESSIONS))
                    .contains(Money.of(46_500));
        }

        @Test
        @DisplayName("한 날짜라도 고를 회차가 없으면 살 수 있는 조합이 없다")
        void noSessionOnADate() {
            Set<Long> withoutMay3 = Set.of(DAY_MAY_4, NIGHT_MAY_4, DAY_MAY_5);

            assertThat(exhibitionPass().startingPrice(exhibitionSchedule(), exhibitionFloorPlan(), withoutMay3)).isEmpty();
        }

        @Test
        @DisplayName("묶은 날짜가 지워져 일정에 없으면 살 수 있는 조합이 없다")
        void retiredDate() {
            BundleProduct product = festivalProduct(terms(Set.of(MAY_3, MAY_4, 599L), Set.of(STANDING_A), 7));

            assertThat(product.startingPrice(festivalSchedule(), festivalFloorPlan(), ALL_FESTIVAL_SESSIONS)).isEmpty();
        }
    }

    @Nested
    @DisplayName("0원이 되는 할인율은 정할 때 막는다 — 가장 싼 회차 조합에 적용해도 1원 이상 (확인 필요 #223)")
    class PositivePrice {

        @Test
        @DisplayName("어린이 2일권(3,000 + 3,000 = 6,000원)에 99% → 60원 → 100원 내림 0원이라 받지 않는다")
        void zeroPriceRejected() {
            assertRuleViolation(() -> BundleProduct.create(FESTIVAL_EVENT_ID, terms(Set.of(MAY_3, MAY_6), Set.of(CHILDREN), 99),
                    festivalSchedule(), festivalFloorPlan()), PricingErrorCode.BUNDLE_PRICE_NOT_POSITIVE);
        }

        @Test
        @DisplayName("98%면 120원 → 100원이라 받는다")
        void positivePriceAllowed() {
            BundleProduct product = BundleProduct.create(FESTIVAL_EVENT_ID, terms(Set.of(MAY_3, MAY_6), Set.of(CHILDREN), 98),
                    festivalSchedule(), festivalFloorPlan());

            assertThat(product.terms().discountRate()).isEqualTo(Percent.of(98));
        }

        @Test
        @DisplayName("구역이 여럿이면 구역마다 본다 — 스탠딩A는 괜찮아도 어린이가 0원이면 받지 않는다")
        void everyZoneChecked() {
            assertRuleViolation(() -> BundleProduct.create(FESTIVAL_EVENT_ID, terms(Set.of(MAY_3, MAY_6), Set.of(STANDING_A, CHILDREN), 99),
                    festivalSchedule(), festivalFloorPlan()), PricingErrorCode.BUNDLE_PRICE_NOT_POSITIVE);
        }

        @Test
        @DisplayName("바꿀 때도 막는다")
        void reconfigureChecked() {
            BundleProduct product = festivalProduct(terms(Set.of(MAY_3, MAY_6), Set.of(CHILDREN), 3));

            assertRuleViolation(() -> product.reconfigure(terms(Set.of(MAY_3, MAY_6), Set.of(CHILDREN), 99), festivalSchedule(),
                    festivalFloorPlan()), PricingErrorCode.BUNDLE_PRICE_NOT_POSITIVE);
            assertThat(product.terms().discountRate()).isEqualTo(Percent.of(3));
        }
    }
}
