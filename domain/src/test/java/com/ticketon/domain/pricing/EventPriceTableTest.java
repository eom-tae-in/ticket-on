package com.ticketon.domain.pricing;

import static com.ticketon.domain.pricing.BundleProductFixtures.CHILDREN;
import static com.ticketon.domain.pricing.BundleProductFixtures.FESTIVAL_EVENT_ID;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_3;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_6;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_7;
import static com.ticketon.domain.pricing.BundleProductFixtures.SESSION_MAY_3;
import static com.ticketon.domain.pricing.BundleProductFixtures.STANDING_A;
import static com.ticketon.domain.pricing.BundleProductFixtures.STANDING_B;
import static com.ticketon.domain.pricing.BundleProductFixtures.TABLE_SEATS;
import static com.ticketon.domain.pricing.BundleProductFixtures.festivalFloorPlan;
import static com.ticketon.domain.pricing.BundleProductFixtures.festivalSchedule;
import static com.ticketon.domain.pricing.BundleProductFixtures.festivalThreeDayPass;
import static com.ticketon.domain.pricing.BundleProductFixtures.terms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventDate;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.EventSession;
import com.ticketon.domain.event.FloorPlan;
import com.ticketon.domain.event.SessionMode;
import com.ticketon.domain.event.SessionPriceAdjustment;
import com.ticketon.domain.event.TimedSessionTime;
import com.ticketon.domain.event.Zone;
import com.ticketon.domain.event.ZoneBasePrice;
import com.ticketon.domain.event.ZoneColor;
import com.ticketon.domain.event.ZoneKind;
import com.ticketon.domain.event.ZoneName;
import com.ticketon.domain.policy.Percent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 행사의 가격표 — 위저드 4단계의 가격 미리보기와, 조기예매 · 쿠폰을 정할 때 · 가격을 바꾼 뒤의 0원 점검(docs/05 "가격의 층위", docs/99 C "할인 값
 * (조기예매·쿠폰)"). 문서의 S석 50,000원 · 3/15(토) +10,000원 · 3/12(수, 프리뷰) -20%(docs/05 "회차별 가격 조정")와 페스티벌 3일권(5/3 40,000 ·
 * 5/4 50,000 · 5/5 50,000원, 7% → 130,200원, docs/05 "가격은 할인율로 계산합니다").
 */
@DisplayName("행사의 가격표 — 미리보기와 0원 점검 (docs/05 \"가격의 층위\", docs/99 C \"할인 값\")")
class EventPriceTableTest {

    private static final long THREE_DAY_PASS_ID = 71L;
    private static final long CHILDREN_PASS_ID = 72L;

    /** 페스티벌 조기예매 · 쿠폰 기간 — 4/1 0시 ~ 4/20 0시. */
    private static final DiscountPeriod APRIL_1_TO_20 = DiscountPeriod.of(korean(2026, 4, 1), korean(2026, 4, 20));
    private static final Instant DURING_DISCOUNT = korean(2026, 4, 10);
    private static final Instant AFTER_DISCOUNT = korean(2026, 4, 25);

    private static Instant korean(int year, int month, int day) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, 0, 0));
    }

    private static Money won(long won) {
        return Money.of(won);
    }

    private static BundleProduct threeDayPass() {
        return BundleProduct.restore(THREE_DAY_PASS_ID, FESTIVAL_EVENT_ID, festivalThreeDayPass());
    }

    private static EventPriceTable festival(BundleProduct... bundleProducts) {
        return EventPriceTable.of(festivalFloorPlan(), Optional.of(festivalSchedule()), List.of(bundleProducts));
    }

    private static EarlyBirdTerms earlyBird(Discount discount, EarlyBirdScope scope) {
        return EarlyBirdTerms.periodOnly(discount, APRIL_1_TO_20, scope);
    }

    private static void assertRejected(ThrowingCallable call, Class<? extends DomainException> type, Object expectedCode) {
        assertThatThrownBy(call).isInstanceOfSatisfying(type, exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    @Nested
    @DisplayName("가격 미리보기")
    class Preview {

        @Test
        @DisplayName("S석 기본 50,000원 — 3/12(수, 프리뷰) -20% 40,000원 · 3/14(금) 조정 없음 50,000원 · 3/15(토) +10,000원 60,000원")
        void sessionPricesFollowAdjustments() {
            FloorPlan floorPlan = FloorPlan.restore(7L, List.of(Zone.restore(11L, ZoneKind.DESIGNATED, ZoneName.of("S석"),
                    ZoneColor.of("#E5484D"), ZoneBasePrice.of(50_000), null)), List.of(), null);
            EventSchedule schedule = EventSchedule.restore(7L, SessionMode.TIMED, null, List.of(
                    timedDate(1L, LocalDate.of(2026, 3, 12), 101L, SessionPriceAdjustment.percent(-20)),
                    timedDate(2L, LocalDate.of(2026, 3, 14), 102L, SessionPriceAdjustment.none()),
                    timedDate(3L, LocalDate.of(2026, 3, 15), 103L, SessionPriceAdjustment.amount(10_000))));

            List<EventPriceTable.SessionZonePrice> prices = EventPriceTable.of(floorPlan, Optional.of(schedule), List.of()).sessionPrices();

            assertThat(prices).containsExactly(
                    new EventPriceTable.SessionZonePrice(101L, 11L, Optional.of(won(40_000))),
                    new EventPriceTable.SessionZonePrice(102L, 11L, Optional.of(won(50_000))),
                    new EventPriceTable.SessionZonePrice(103L, 11L, Optional.of(won(60_000))));
        }

        @Test
        @DisplayName("0원 이하가 되는 조합은 가격이 비어 있다 — 어린이 3,000원에 -5,000원 조정")
        void invalidCombinationHasNoPrice() {
            EventSchedule schedule = EventSchedule.restore(FESTIVAL_EVENT_ID, SessionMode.ALL_DAY, festivalSchedule().defaultOperatingHours()
                    .orElseThrow(), List.of(EventDate.restore(MAY_3, LocalDate.of(2026, 5, 3), null, List.of(
                    EventSession.restore(SESSION_MAY_3, null, null, SessionPriceAdjustment.amount(-5_000), null)))));

            List<EventPriceTable.SessionZonePrice> prices = EventPriceTable.of(festivalFloorPlan(), Optional.of(schedule), List.of())
                    .sessionPrices();

            assertThat(prices).filteredOn(price -> price.zoneId() == CHILDREN).singleElement()
                    .extracting(EventPriceTable.SessionZonePrice::price).isEqualTo(Optional.empty());
            assertThat(prices).filteredOn(price -> price.zoneId() == STANDING_A).singleElement()
                    .extracting(EventPriceTable.SessionZonePrice::price).isEqualTo(Optional.of(won(35_000)));
        }

        @Test
        @DisplayName("3일권 시작 가격 — 40,000 + 50,000 + 50,000 = 140,000원 → 7% → 130,200원")
        void bundleStartingPrice() {
            assertThat(festival(threeDayPass()).startingPriceOf(threeDayPass())).hasValue(won(130_200));
        }

        @Test
        @DisplayName("지워진 날짜를 묶은 묶음권은 고를 회차가 없어 시작 가격이 비어 있다")
        void bundleWithRetiredDateHasNoStartingPrice() {
            BundleProduct retired = BundleProduct.restore(THREE_DAY_PASS_ID, FESTIVAL_EVENT_ID, terms(Set.of(MAY_3, 99L), Set.of(STANDING_A), 3));

            assertThat(festival(retired).startingPriceOf(retired)).isEmpty();
        }

        @Test
        @DisplayName("회차 방식을 아직 고르지 않은 행사(일정 없음)에는 회차 가격이 없다")
        void noScheduleNoPrices() {
            EventPriceTable table = EventPriceTable.of(festivalFloorPlan(), Optional.empty(), List.of());

            assertThat(table.sessionPrices()).isEmpty();
            assertThat(table.lowestPrice()).isEmpty();
        }

        @Test
        @DisplayName("다른 행사의 일정 · 묶음권은 받지 않는다")
        void rejectsOtherEvents() {
            EventSchedule otherSchedule = EventSchedule.restore(99L, SessionMode.TIMED, null, List.of());
            BundleProduct otherBundle = BundleProduct.restore(1L, 99L, festivalThreeDayPass());

            assertThatIllegalArgumentException().isThrownBy(() -> EventPriceTable.of(festivalFloorPlan(), Optional.of(otherSchedule),
                    List.of()));
            assertThatIllegalArgumentException().isThrownBy(() -> EventPriceTable.of(festivalFloorPlan(), Optional.of(festivalSchedule()),
                    List.of(otherBundle)));
        }
    }

    @Nested
    @DisplayName("할인이 닿는 가장 낮은 가격")
    class LowestPrice {

        @Test
        @DisplayName("쿠폰은 그 행사의 모든 가격 — 어린이 5/3 3,000원")
        void couponReachesEveryPrice() {
            assertThat(festival(threeDayPass()).lowestPrice()).hasValue(won(3_000));
        }

        @Test
        @DisplayName("1일권을 끄고 3일권만 켠 조기예매(\"얼리버드 3일권만\")는 3일권 130,200원이 가장 낮다")
        void threeDayPassOnly() {
            EarlyBirdScope threeDayPassOnly = EarlyBirdScope.everything().withoutProduct(TicketProduct.singleSession());

            assertThat(festival(threeDayPass()).lowestPriceReachedBy(threeDayPassOnly)).hasValue(won(130_200));
        }

        @Test
        @DisplayName("어린이 구역을 끄면 스탠딩B 5/3 30,000원이 가장 낮다")
        void excludedZoneIsSkipped() {
            EarlyBirdScope withoutChildren = EarlyBirdScope.everything().withoutZone(CHILDREN);

            assertThat(festival().lowestPriceReachedBy(withoutChildren)).hasValue(won(30_000));
        }
    }

    @Nested
    @DisplayName("조기예매 · 쿠폰을 정할 때")
    class Applicability {

        @Test
        @DisplayName("정액 3,000원은 어린이 3,000원을 0원으로 만들어 받지 않고, 어린이 구역을 끄면 받는다")
        void earlyBirdMustLeavePositivePrice() {
            Discount threeThousand = Discount.fixedAmount(won(3_000));

            assertRejected(() -> festival().requireEarlyBirdApplicable(earlyBird(threeThousand, EarlyBirdScope.everything())),
                    RuleViolationException.class, PricingErrorCode.DISCOUNT_MAKES_PRICE_ZERO);
            assertThatNoException().isThrownBy(() -> festival().requireEarlyBirdApplicable(
                    earlyBird(threeThousand, EarlyBirdScope.everything().withoutZone(CHILDREN))));
        }

        @Test
        @DisplayName("끈 구역이 도면에 없으면 ZONE_NOT_FOUND, 끈 묶음권이 이 행사에 없으면 BUNDLE_PRODUCT_NOT_FOUND")
        void excludedTargetsMustExist() {
            Discount tenPercent = Discount.rate(Percent.of(10));

            assertRejected(() -> festival().requireEarlyBirdApplicable(earlyBird(tenPercent, EarlyBirdScope.everything().withoutZone(404L))),
                    NotFoundException.class, EventErrorCode.ZONE_NOT_FOUND);
            assertRejected(() -> festival(threeDayPass()).requireEarlyBirdApplicable(earlyBird(tenPercent,
                    EarlyBirdScope.everything().withoutProduct(TicketProduct.bundle(404L)))), NotFoundException.class,
                    PricingErrorCode.BUNDLE_PRODUCT_NOT_FOUND);
        }

        @Test
        @DisplayName("모든 구역을 끄거나, 1일권을 끄고 묶음권이 없으면 아무 티켓에도 닿지 않아 받지 않는다 (확인 필요 #223)")
        void scopeMustReachSomeTicket() {
            Discount tenPercent = Discount.rate(Percent.of(10));
            EarlyBirdScope allZonesOff = EarlyBirdScope.everything().withoutZone(STANDING_A).withoutZone(STANDING_B)
                    .withoutZone(CHILDREN).withoutZone(TABLE_SEATS);
            EarlyBirdScope singleSessionOff = EarlyBirdScope.everything().withoutProduct(TicketProduct.singleSession());

            assertRejected(() -> festival().requireEarlyBirdApplicable(earlyBird(tenPercent, allZonesOff)), RuleViolationException.class,
                    PricingErrorCode.EARLY_BIRD_SCOPE_EMPTY);
            assertRejected(() -> festival().requireEarlyBirdApplicable(earlyBird(tenPercent, singleSessionOff)),
                    RuleViolationException.class, PricingErrorCode.EARLY_BIRD_SCOPE_EMPTY);
            assertThatNoException().isThrownBy(() -> festival(threeDayPass()).requireEarlyBirdApplicable(earlyBird(tenPercent,
                    singleSessionOff)));
        }

        @Test
        @DisplayName("3일권만 켰는데 3일권의 구역(스탠딩A)을 끄면 닿는 티켓이 없다")
        void bundleZoneOffReachesNothing() {
            EarlyBirdScope scope = EarlyBirdScope.everything().withoutProduct(TicketProduct.singleSession()).withoutZone(STANDING_A);

            assertRejected(() -> festival(threeDayPass()).requireEarlyBirdApplicable(earlyBird(Discount.rate(Percent.of(10)), scope)),
                    RuleViolationException.class, PricingErrorCode.EARLY_BIRD_SCOPE_EMPTY);
        }

        @Test
        @DisplayName("구역이 아직 없으면 범위와 0원을 보지 않는다")
        void noZonesYet() {
            EventPriceTable empty = EventPriceTable.of(FloorPlan.empty(FESTIVAL_EVENT_ID), Optional.of(festivalSchedule()), List.of());

            assertThatNoException().isThrownBy(() -> empty.requireEarlyBirdApplicable(earlyBird(Discount.fixedAmount(won(100_000)),
                    EarlyBirdScope.everything().withoutProduct(TicketProduct.singleSession()))));
        }

        @Test
        @DisplayName("쿠폰 정액 3,000원은 어린이 3,000원을 0원으로 만들어 받지 않고, 2,999원은 받는다")
        void couponMustLeavePositivePrice() {
            assertRejected(() -> festival().requireCouponApplicable(new CouponTerms(Discount.fixedAmount(won(3_000)), 100, false,
                    APRIL_1_TO_20)), RuleViolationException.class, PricingErrorCode.DISCOUNT_MAKES_PRICE_ZERO);
            assertThatNoException().isThrownBy(() -> festival().requireCouponApplicable(new CouponTerms(
                    Discount.fixedAmount(won(2_999)), 100, false, APRIL_1_TO_20)));
        }
    }

    @Nested
    @DisplayName("가격을 바꾼 뒤의 점검")
    class AfterPriceChange {

        private final EarlyBirds noEarlyBirds = EarlyBirds.of(List.of());

        @Test
        @DisplayName("50,000원 구역에 -60,000원 조정처럼 0원 이하가 되는 회차 가격이 있으면 SESSION_PRICE_NOT_POSITIVE")
        void sessionPriceMustStayPositive() {
            EventSchedule schedule = EventSchedule.restore(FESTIVAL_EVENT_ID, SessionMode.ALL_DAY, festivalSchedule().defaultOperatingHours()
                    .orElseThrow(), List.of(EventDate.restore(MAY_3, LocalDate.of(2026, 5, 3), null, List.of(
                    EventSession.restore(SESSION_MAY_3, null, null, SessionPriceAdjustment.amount(-60_000), null)))));
            EventPriceTable table = EventPriceTable.of(festivalFloorPlan(), Optional.of(schedule), List.of());

            assertRejected(() -> table.requirePricesStayPositive(noEarlyBirds, List.of(), DURING_DISCOUNT), RuleViolationException.class,
                    PricingErrorCode.SESSION_PRICE_NOT_POSITIVE);
        }

        @Test
        @DisplayName("어린이(3,000원 × 3일 = 9,000원) 99% 묶음권은 90원 → 0원이라 막는다")
        void bundlePriceMustStayPositive() {
            BundleProduct childrenPass = BundleProduct.restore(CHILDREN_PASS_ID, FESTIVAL_EVENT_ID,
                    terms(Set.of(MAY_3, MAY_6, MAY_7), Set.of(CHILDREN), 99));

            assertRejected(() -> festival(childrenPass).requirePricesStayPositive(noEarlyBirds, List.of(), DURING_DISCOUNT),
                    RuleViolationException.class, PricingErrorCode.PRICE_CHANGE_MAKES_DISCOUNTED_PRICE_ZERO);
        }

        @Test
        @DisplayName("진행 중인 정액 3,000원 조기예매는 막고, 기간이 끝난 뒤에는 보지 않는다 (확인 필요 #223)")
        void openEarlyBirdOnly() {
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(EarlyBird.restore(5L, FESTIVAL_EVENT_ID,
                    earlyBird(Discount.fixedAmount(won(3_000)), EarlyBirdScope.everything()), 0, EarlyBirdUrgency.NONE)));

            assertRejected(() -> festival().requirePricesStayPositive(earlyBirds, List.of(), DURING_DISCOUNT), RuleViolationException.class,
                    PricingErrorCode.PRICE_CHANGE_MAKES_DISCOUNTED_PRICE_ZERO);
            assertThatNoException().isThrownBy(() -> festival().requirePricesStayPositive(earlyBirds, List.of(), AFTER_DISCOUNT));
        }

        @Test
        @DisplayName("유효 기간이 끝나지 않은 정액 3,000원 쿠폰은 막고, 끝난 뒤에는 보지 않는다")
        void validCouponOnly() {
            Coupon coupon = Coupon.restore(6L, FESTIVAL_EVENT_ID, CouponCode.of("FAN3000"),
                    new CouponTerms(Discount.fixedAmount(won(3_000)), 100, true, APRIL_1_TO_20), 0);

            assertRejected(() -> festival().requirePricesStayPositive(noEarlyBirds, List.of(coupon), DURING_DISCOUNT),
                    RuleViolationException.class, PricingErrorCode.PRICE_CHANGE_MAKES_DISCOUNTED_PRICE_ZERO);
            assertThatNoException().isThrownBy(() -> festival().requirePricesStayPositive(noEarlyBirds, List.of(coupon), AFTER_DISCOUNT));
        }

        @Test
        @DisplayName("모든 가격이 1원 이상 남으면 통과한다 — 3일권 7%, 10% 조기예매, 2,000원 쿠폰")
        void passesWhenEverythingPositive() {
            EarlyBirds earlyBirds = EarlyBirds.of(List.of(EarlyBird.restore(5L, FESTIVAL_EVENT_ID,
                    earlyBird(Discount.rate(Percent.of(10)), EarlyBirdScope.everything()), 0, EarlyBirdUrgency.NONE)));
            Coupon coupon = Coupon.restore(6L, FESTIVAL_EVENT_ID, CouponCode.of("FAN2000"),
                    new CouponTerms(Discount.fixedAmount(won(2_000)), 100, true, APRIL_1_TO_20), 0);

            assertThatNoException().isThrownBy(() -> festival(threeDayPass()).requirePricesStayPositive(earlyBirds, List.of(coupon),
                    DURING_DISCOUNT));
        }
    }

    private static EventDate timedDate(long dateId, LocalDate date, long sessionId, SessionPriceAdjustment adjustment) {
        return EventDate.restore(dateId, date, null, List.of(EventSession.restore(sessionId,
                TimedSessionTime.startingAt(LocalTime.of(19, 0)), null, adjustment, null)));
    }
}
