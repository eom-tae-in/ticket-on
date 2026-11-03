package com.ticketon.domain.pricing;

import static com.ticketon.domain.pricing.BundleProductFixtures.CHILDREN;
import static com.ticketon.domain.pricing.BundleProductFixtures.FESTIVAL_EVENT_ID;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_3;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_4;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_5;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_6;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_7;
import static com.ticketon.domain.pricing.BundleProductFixtures.SESSION_MAY_3;
import static com.ticketon.domain.pricing.BundleProductFixtures.SESSION_MAY_4;
import static com.ticketon.domain.pricing.BundleProductFixtures.SESSION_MAY_5;
import static com.ticketon.domain.pricing.BundleProductFixtures.STANDING_A;
import static com.ticketon.domain.pricing.BundleProductFixtures.STANDING_B;
import static com.ticketon.domain.pricing.BundleProductFixtures.festivalFloorPlan;
import static com.ticketon.domain.pricing.BundleProductFixtures.festivalSchedule;
import static com.ticketon.domain.pricing.BundleProductFixtures.terms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.policy.Percent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 행사의 도면 · 상품이 바뀔 때 가격 쪽이 따라가는 것 — 지워진 구역 · 묶음권 상품을 가리키지 않게 정리하고, 바뀐 가격으로 묶음권 가격을 다시 보고,
 * 한 행사 안에서 쿠폰 코드가 겹치지 않게 한다(#51). 페스티벌 스탠딩A 40,000원(금) · 50,000원(토 · 일)과 3일권(docs/05 "가격은 할인율로 계산합니다").
 */
@DisplayName("가격 쪽 정리 — 지워진 구역 · 상품, 묶음권 0원 점검, 쿠폰 코드 겹침")
class PricingMaintenanceTest {

    private static final DiscountPeriod APRIL = DiscountPeriod.of(korean(2026, 4, 1), korean(2026, 4, 20));
    private static final Instant APRIL_10 = korean(2026, 4, 10);

    private static Instant korean(int year, int month, int day) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, 0, 0));
    }

    @Nested
    @DisplayName("구역을 지우면 묶음권의 대상 구역에서 뺀다")
    class BundleZoneRemoval {

        @Test
        @DisplayName("스탠딩A · 스탠딩B 3일권에서 스탠딩B를 지우면 스탠딩A만 남는다")
        void dropsRemovedZone() {
            BundleProduct pass = BundleProduct.restore(71L, FESTIVAL_EVENT_ID, terms(Set.of(MAY_3, MAY_4, MAY_5), Set.of(STANDING_A, STANDING_B), 7));

            assertThat(pass.dropRemovedZone(STANDING_B)).isTrue();

            assertThat(pass.terms().zoneIds()).containsExactly(STANDING_A);
            assertThat(pass.terms().dateIds()).containsExactlyInAnyOrder(MAY_3, MAY_4, MAY_5);
        }

        @Test
        @DisplayName("팔지 않는 구역을 지우면 그대로다")
        void unrelatedZone() {
            BundleProduct pass = BundleProduct.restore(71L, FESTIVAL_EVENT_ID, terms(Set.of(MAY_3, MAY_4), Set.of(STANDING_A), 3));

            assertThat(pass.dropRemovedZone(CHILDREN)).isFalse();
            assertThat(pass.terms().zoneIds()).containsExactly(STANDING_A);
        }

        @Test
        @DisplayName("하나뿐인 대상 구역은 빼지 않고 지운 구역을 그대로 가리킨다 — 구역 삭제는 막지 않고, 팔 구역이 없어 시작 가격이 없다 (확인 필요 #223)")
        void onlyZoneIsKept() {
            BundleProduct pass = BundleProduct.restore(71L, FESTIVAL_EVENT_ID, terms(Set.of(MAY_3, MAY_4), Set.of(STANDING_A), 3));
            FloorPlan withoutStandingA = FloorPlan.restore(FESTIVAL_EVENT_ID, festivalFloorPlan().zones().stream()
                    .filter(zone -> zone.id().getAsLong() != STANDING_A).toList(), List.of(), null);

            assertThat(pass.dropRemovedZone(STANDING_A)).isFalse();

            assertThat(pass.terms().zoneIds()).containsExactly(STANDING_A);
            assertThat(pass.startingPrice(festivalSchedule(), withoutStandingA, Set.of(SESSION_MAY_3, SESSION_MAY_4))).isEmpty();
            assertThat(pass.leavesPositivePrice(festivalSchedule(), withoutStandingA)).isTrue();
        }
    }

    @Nested
    @DisplayName("묶음권 가격을 다시 본다")
    class BundlePrice {

        @Test
        @DisplayName("구역 하나의 시작 가격 — 스탠딩A 3일권 7% 130,200원, 스탠딩B(30,000 · 40,000 · 40,000원) 7% 102,300원")
        void cheapestPriceInZone() {
            BundleProduct pass = BundleProduct.restore(71L, FESTIVAL_EVENT_ID, terms(Set.of(MAY_3, MAY_4, MAY_5), Set.of(STANDING_A, STANDING_B), 7));
            Set<Long> sessions = Set.of(SESSION_MAY_3, SESSION_MAY_4, SESSION_MAY_5);

            assertThat(pass.cheapestPriceIn(STANDING_A, festivalSchedule(), festivalFloorPlan(), sessions)).hasValue(Money.of(130_200));
            assertThat(pass.cheapestPriceIn(STANDING_B, festivalSchedule(), festivalFloorPlan(), sessions)).hasValue(Money.of(102_300));
            assertThat(pass.cheapestPriceIn(CHILDREN, festivalSchedule(), festivalFloorPlan(), sessions)).isEmpty();
        }

        @Test
        @DisplayName("어린이(3,000원 × 3일 = 9,000원) 98%는 180원 → 100원, 99%는 90원 → 0원")
        void leavesPositivePrice() {
            BundleProduct ninetyEight = BundleProduct.restore(72L, FESTIVAL_EVENT_ID, terms(Set.of(MAY_3, MAY_6, MAY_7), Set.of(CHILDREN), 98));
            BundleProduct ninetyNine = BundleProduct.restore(72L, FESTIVAL_EVENT_ID, terms(Set.of(MAY_3, MAY_6, MAY_7), Set.of(CHILDREN), 99));

            assertThat(ninetyEight.leavesPositivePrice(festivalSchedule(), festivalFloorPlan())).isTrue();
            assertThat(ninetyNine.leavesPositivePrice(festivalSchedule(), festivalFloorPlan())).isFalse();
        }

        @Test
        @DisplayName("날짜별 가격 합계 9,000원 — 99%는 0원이라 1원 이상 남기지 않는다")
        void dayPricesLeavePositivePrice() {
            BundleDayPrices dayPrices = BundleDayPrices.of(List.of(SessionPrice.of(ZoneBasePrice.of(9_000),
                    SessionPriceAdjustment.none())));

            assertThat(dayPrices.leavesPositivePrice(Percent.of(98))).isTrue();
            assertThat(dayPrices.leavesPositivePrice(Percent.of(99))).isFalse();
        }
    }

    @Nested
    @DisplayName("조기예매의 끈 구역 · 상품에서 지워진 것을 지운다")
    class EarlyBirdScopeCleanup {

        private EarlyBird closedEarlyBirdExcluding(EarlyBirdScope scope) {
            return EarlyBird.restore(5L, FESTIVAL_EVENT_ID, EarlyBirdTerms.limited(Discount.rate(Percent.of(10)), APRIL,
                    EarlyBirdQuantity.of(30), scope), 30, EarlyBirdUrgency.ALMOST_GONE);
        }

        @Test
        @DisplayName("소진된 조기예매라도 지워진 구역을 끈 기록은 지운다 — 닿는 티켓이 바뀌지 않는다")
        void dropsRemovedZoneEvenWhenClosed() {
            EarlyBird earlyBird = closedEarlyBirdExcluding(EarlyBirdScope.everything().withoutZone(CHILDREN).withoutZone(STANDING_B));
            assertThat(earlyBird.status(APRIL_10)).isEqualTo(EarlyBirdStatus.SOLD_OUT);

            assertThat(earlyBird.dropRemovedZone(CHILDREN)).isTrue();
            assertThat(earlyBird.dropRemovedZone(STANDING_A)).isFalse();

            assertThat(earlyBird.terms().scope().excludedZoneIds()).containsExactly(STANDING_B);
            assertThat(earlyBird.claimedCount()).isEqualTo(30);
            assertThat(earlyBird.highestUrgencyReached()).isEqualTo(EarlyBirdUrgency.ALMOST_GONE);
        }

        @Test
        @DisplayName("지운 묶음권 상품을 끈 기록도 지운다 — 1일권을 끈 기록은 남는다")
        void dropsRemovedBundleProduct() {
            EarlyBird earlyBird = closedEarlyBirdExcluding(EarlyBirdScope.everything().withoutProduct(TicketProduct.bundle(71L))
                    .withoutProduct(TicketProduct.singleSession()));

            assertThat(earlyBird.dropRemovedBundleProduct(71L)).isTrue();
            assertThat(earlyBird.dropRemovedBundleProduct(72L)).isFalse();

            assertThat(earlyBird.terms().scope().excludedProducts()).containsExactly(TicketProduct.singleSession());
        }
    }

    @Nested
    @DisplayName("한 행사 안에서 쿠폰 코드는 겹치지 않는다")
    class CouponCodeUniqueness {

        private final CouponTerms fanCoupon = new CouponTerms(Discount.fixedAmount(Money.of(5_000)), 100, true, APRIL);

        @Test
        @DisplayName("SPRING20이 있으면 spring20 · Spring20도 같은 코드라 받지 않는다")
        void caseInsensitiveDuplicate() {
            List<Coupon> coupons = List.of(Coupon.restore(1L, FESTIVAL_EVENT_ID, CouponCode.of("SPRING20"), fanCoupon, 0));

            for (String input : List.of("SPRING20", "spring20", " Spring20 ")) {
                assertThatThrownBy(() -> Coupon.issueAmong(coupons, FESTIVAL_EVENT_ID, CouponCode.of(input), fanCoupon))
                        .isInstanceOfSatisfying(RuleViolationException.class,
                                exception -> assertThat(exception.errorCode()).isEqualTo(PricingErrorCode.COUPON_CODE_ALREADY_TAKEN));
            }
        }

        @Test
        @DisplayName("다른 코드는 발급한다 — 아직 쓴 사람이 없다")
        void issuesNewCode() {
            List<Coupon> coupons = List.of(Coupon.restore(1L, FESTIVAL_EVENT_ID, CouponCode.of("SPRING20"), fanCoupon, 3));

            Coupon issued = Coupon.issueAmong(coupons, FESTIVAL_EVENT_ID, CouponCode.of("FANHOLIDAY"), fanCoupon);

            assertThat(issued.id()).isEmpty();
            assertThat(issued.code().value()).isEqualTo("FANHOLIDAY");
            assertThat(issued.usedCount()).isZero();
        }

        @Test
        @DisplayName("다른 행사의 쿠폰은 넘기지 않는다")
        void otherEventCoupons() {
            List<Coupon> coupons = List.of(Coupon.restore(1L, 99L, CouponCode.of("SPRING20"), fanCoupon, 0));

            assertThatIllegalArgumentException().isThrownBy(() -> Coupon.issueAmong(coupons, FESTIVAL_EVENT_ID, CouponCode.of("OTHER"),
                    fanCoupon));
        }
    }
}
