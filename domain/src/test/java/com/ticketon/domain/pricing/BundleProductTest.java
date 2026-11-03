package com.ticketon.domain.pricing;

import static com.ticketon.domain.pricing.BundleProductFixtures.EXHIBITION_EVENT_ID;
import static com.ticketon.domain.pricing.BundleProductFixtures.FESTIVAL_EVENT_ID;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_3;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_4;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_5;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_6;
import static com.ticketon.domain.pricing.BundleProductFixtures.MAY_7;
import static com.ticketon.domain.pricing.BundleProductFixtures.STANDING_A;
import static com.ticketon.domain.pricing.BundleProductFixtures.STANDING_B;
import static com.ticketon.domain.pricing.BundleProductFixtures.TABLE_SEATS;
import static com.ticketon.domain.pricing.BundleProductFixtures.exhibitionFloorPlan;
import static com.ticketon.domain.pricing.BundleProductFixtures.exhibitionSchedule;
import static com.ticketon.domain.pricing.BundleProductFixtures.festivalFloorPlan;
import static com.ticketon.domain.pricing.BundleProductFixtures.festivalSchedule;
import static com.ticketon.domain.pricing.BundleProductFixtures.festivalThreeDayPass;
import static com.ticketon.domain.pricing.BundleProductFixtures.terms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.event.EventErrorCode;
import com.ticketon.domain.policy.Percent;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("묶음권 상품 — 날짜 N개 · 수량형 구역 · 할인율 (docs/05 \"묶음권 (수량형 전용)\", docs/14)")
class BundleProductTest {

    private static BundleProduct createFestivalProduct(BundleProductTerms terms) {
        return BundleProduct.create(FESTIVAL_EVENT_ID, terms, festivalSchedule(), festivalFloorPlan());
    }

    private static void assertRuleViolation(ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable).isInstanceOfSatisfying(RuleViolationException.class,
                exception -> assertThat(exception.errorCode()).isEqualTo(expected));
    }

    @Nested
    @DisplayName("묶음권 만들기 — 주최자는 날짜만 고른다 (docs/05 \"조합은 자동 생성하지 않습니다\")")
    class Create {

        @Test
        @DisplayName("이름: 3일 통권 · 포함 날짜: 5/3 · 5/4 · 5/5 · 할인율: 7%")
        void organizerDefinesDates() {
            BundleProduct product = createFestivalProduct(festivalThreeDayPass());

            assertThat(product.id()).isEmpty();
            assertThat(product.eventId()).isEqualTo(FESTIVAL_EVENT_ID);
            assertThat(product.terms().name().value()).isEqualTo("3일 통권");
            assertThat(product.terms().dateIds()).containsExactlyInAnyOrder(MAY_3, MAY_4, MAY_5);
            assertThat(product.terms().dateCount()).isEqualTo(3);
            assertThat(product.terms().zoneIds()).containsExactly(STANDING_A);
            assertThat(product.terms().discountRate()).isEqualTo(Percent.of(7));
        }

        @Test
        @DisplayName("시간대를 나눈 전시(회차형)에서도 날짜를 묶는다 — 날짜 아래 회차는 관객이 고른다")
        void timedExhibition() {
            BundleProduct product = BundleProduct.create(EXHIBITION_EVENT_ID, BundleProductFixtures.exhibitionThreeDayPass(),
                    exhibitionSchedule(), exhibitionFloorPlan());

            assertThat(product.terms().dateCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("수량형 구역 여럿에 팔 수 있다 — 스탠딩A · 스탠딩B")
        void severalQuantityZones() {
            BundleProduct product = createFestivalProduct(terms(Set.of(MAY_3, MAY_4), Set.of(STANDING_A, STANDING_B), 3));

            assertThat(product.terms().zoneIds()).containsExactlyInAnyOrder(STANDING_A, STANDING_B);
        }

        @Test
        @DisplayName("지정형 구역은 받지 않는다 — 날짜 수만큼 좌석을 동시에 선점해야 한다 (docs/05 \"왜 수량형 전용인가\")")
        void designatedZoneRejected() {
            assertRuleViolation(() -> createFestivalProduct(terms(Set.of(MAY_3, MAY_4, MAY_5), Set.of(TABLE_SEATS), 7)),
                    PricingErrorCode.BUNDLE_ZONE_NOT_QUANTITY);
            assertRuleViolation(() -> createFestivalProduct(terms(Set.of(MAY_3, MAY_4, MAY_5), Set.of(STANDING_A, TABLE_SEATS), 7)),
                    PricingErrorCode.BUNDLE_ZONE_NOT_QUANTITY);
        }

        @Test
        @DisplayName("날짜가 1개면 받지 않는다 — 날짜 하나짜리는 1일권이다")
        void singleDateRejected() {
            assertRuleViolation(() -> terms(Set.of(MAY_3), Set.of(STANDING_A), 3), PricingErrorCode.BUNDLE_DATES_TOO_FEW);
            assertRuleViolation(() -> terms(Set.of(), Set.of(STANDING_A), 3), PricingErrorCode.BUNDLE_DATES_TOO_FEW);
        }

        @Test
        @DisplayName("팔 구역이 없으면 받지 않는다")
        void noZoneRejected() {
            assertRuleViolation(() -> terms(Set.of(MAY_3, MAY_4), Set.of(), 3), PricingErrorCode.BUNDLE_ZONES_EMPTY);
        }

        @Test
        @DisplayName("할인율 100%는 받지 않는다 — 어느 가격이든 0원이고, 돈을 받지 않는 표는 초대권이다")
        void wholeDiscountRejected() {
            assertRuleViolation(() -> terms(Set.of(MAY_3, MAY_4), Set.of(STANDING_A), 100), PricingErrorCode.BUNDLE_PRICE_NOT_POSITIVE);
        }

        @Test
        @DisplayName("할인율 0%는 받는다 — 할인 없이 묶어 판다")
        void zeroDiscountAllowed() {
            BundleProduct product = createFestivalProduct(terms(Set.of(MAY_3, MAY_4), Set.of(STANDING_A), 0));

            assertThat(product.terms().discountRate()).isEqualTo(Percent.of(0));
        }

        @Test
        @DisplayName("일정에 없는 날짜, 도면에 없는 구역은 찾을 수 없다")
        void unknownDateOrZone() {
            assertThatThrownBy(() -> createFestivalProduct(terms(Set.of(MAY_3, 999L), Set.of(STANDING_A), 3)))
                    .isInstanceOfSatisfying(NotFoundException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(EventErrorCode.SCHEDULE_DATE_NOT_FOUND));
            assertThatThrownBy(() -> createFestivalProduct(terms(Set.of(MAY_3, MAY_4), Set.of(999L), 3)))
                    .isInstanceOfSatisfying(NotFoundException.class,
                            exception -> assertThat(exception.errorCode()).isEqualTo(EventErrorCode.ZONE_NOT_FOUND));
        }

        @Test
        @DisplayName("다른 행사의 일정 · 도면으로는 만들지 않는다")
        void otherEventRejected() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> BundleProduct.create(FESTIVAL_EVENT_ID, festivalThreeDayPass(), exhibitionSchedule(), festivalFloorPlan()));
            assertThatIllegalArgumentException().isThrownBy(
                    () -> BundleProduct.create(FESTIVAL_EVENT_ID, festivalThreeDayPass(), festivalSchedule(), exhibitionFloorPlan()));
        }
    }

    @Nested
    @DisplayName("기본 할인율 — 2일 3% · 3일 7% · 4일 10% (docs/05, docs/99 C)")
    class DefaultDiscountRate {

        @Test
        @DisplayName("날짜 수에 따라 채운다")
        void filledByDateCount() {
            assertThat(BundleProduct.defaultDiscountRate(2)).contains(Percent.of(3));
            assertThat(BundleProduct.defaultDiscountRate(3)).contains(Percent.of(7));
            assertThat(BundleProduct.defaultDiscountRate(4)).contains(Percent.of(10));
        }

        @Test
        @DisplayName("5일 이상은 채우지 않는다 — 기본값과 상한은 #52에서 정한다")
        void fiveDaysOrMoreLeftEmpty() {
            assertThat(BundleProduct.defaultDiscountRate(5)).isEmpty();
            assertThat(BundleProduct.defaultDiscountRate(10)).isEmpty();
        }

        @Test
        @DisplayName("날짜가 2개보다 적은 묶음권은 없다")
        void fewerThanTwoDates() {
            assertThatIllegalArgumentException().isThrownBy(() -> BundleProduct.defaultDiscountRate(1));
        }

        @Test
        @DisplayName("주최자가 바꿀 수 있다 — 3일권 15%")
        void organizerOverrides() {
            BundleProduct product = createFestivalProduct(terms(Set.of(MAY_3, MAY_4, MAY_5), Set.of(STANDING_A), 15));

            assertThat(product.terms().discountRate()).isEqualTo(Percent.of(15));
        }

        @Test
        @DisplayName("5일 이상 묶음권도 주최자가 적은 할인율로 만든다")
        void fiveDayPassWithOrganizerRate() {
            BundleProduct product = createFestivalProduct(terms(Set.of(MAY_3, MAY_4, MAY_5, MAY_6, MAY_7), Set.of(STANDING_A), 20));

            assertThat(product.terms().dateCount()).isEqualTo(5);
            assertThat(product.terms().discountRate()).isEqualTo(Percent.of(20));
        }
    }

    @Nested
    @DisplayName("설정 바꾸기")
    class Reconfigure {

        @Test
        @DisplayName("할인율을 바꾼다 — 3일 통권 7% → 10%")
        void changeDiscount() {
            BundleProduct product = BundleProduct.restore(11L, FESTIVAL_EVENT_ID, festivalThreeDayPass());

            boolean changed = product.reconfigure(terms(Set.of(MAY_3, MAY_4, MAY_5), Set.of(STANDING_A), 10), festivalSchedule(),
                    festivalFloorPlan());

            assertThat(changed).isTrue();
            assertThat(product.terms().discountRate()).isEqualTo(Percent.of(10));
        }

        @Test
        @DisplayName("같은 설정이면 바뀌지 않는다")
        void sameTerms() {
            BundleProduct product = BundleProduct.restore(11L, FESTIVAL_EVENT_ID, festivalThreeDayPass());

            assertThat(product.reconfigure(festivalThreeDayPass(), festivalSchedule(), festivalFloorPlan())).isFalse();
        }

        @Test
        @DisplayName("바꿀 때도 지정형 구역은 받지 않고, 원래 설정이 남는다")
        void designatedZoneRejected() {
            BundleProduct product = BundleProduct.restore(11L, FESTIVAL_EVENT_ID, festivalThreeDayPass());

            assertRuleViolation(() -> product.reconfigure(terms(Set.of(MAY_3, MAY_4, MAY_5), Set.of(TABLE_SEATS), 7), festivalSchedule(),
                    festivalFloorPlan()), PricingErrorCode.BUNDLE_ZONE_NOT_QUANTITY);
            assertThat(product.terms()).isEqualTo(festivalThreeDayPass());
        }
    }

    @Nested
    @DisplayName("날짜를 옮기면 따라간다 — 상품은 날짜를 가리킨다 (docs/14 \"무엇을 가리키고 무엇을 직접 갖나\")")
    class FollowMovedDate {

        @Test
        @DisplayName("종일형 5/4를 5/11로 연기해 날짜가 새로 생기면 3일권도 5/3 · 5/11 · 5/5를 묶는다")
        void followsPostponedDay() {
            BundleProduct product = BundleProduct.restore(11L, FESTIVAL_EVENT_ID, festivalThreeDayPass());
            long may11 = 511L;

            boolean changed = product.followMovedDate(MAY_4, may11);

            assertThat(changed).isTrue();
            assertThat(product.terms().dateIds()).containsExactlyInAnyOrder(MAY_3, may11, MAY_5);
            assertThat(product.terms().name()).isEqualTo(festivalThreeDayPass().name());
            assertThat(product.terms().zoneIds()).isEqualTo(festivalThreeDayPass().zoneIds());
            assertThat(product.terms().discountRate()).isEqualTo(Percent.of(7));
        }

        @Test
        @DisplayName("묶지 않은 날짜가 옮겨 가면 그대로다")
        void unrelatedDate() {
            BundleProduct product = BundleProduct.restore(11L, FESTIVAL_EVENT_ID, festivalThreeDayPass());

            assertThat(product.followMovedDate(MAY_6, 512L)).isFalse();
            assertThat(product.terms()).isEqualTo(festivalThreeDayPass());
        }

        @Test
        @DisplayName("이미 묶은 날짜로 합쳐지면 따라가지 않는다 — 3일권이 말없이 2일권이 되지 않게 (확인 필요 #223)")
        void doesNotMergeIntoIncludedDate() {
            BundleProduct product = BundleProduct.restore(11L, FESTIVAL_EVENT_ID, festivalThreeDayPass());

            assertThat(product.followMovedDate(MAY_5, MAY_4)).isFalse();
            assertThat(product.terms().dateIds()).containsExactlyInAnyOrder(MAY_3, MAY_4, MAY_5);
        }
    }

    @Nested
    @DisplayName("조기예매가 가리키는 상품 (docs/05 \"상품별로도 끌 수 있습니다\")")
    class Product {

        @Test
        @DisplayName("저장한 묶음권은 그 id로 가리킨다 — 얼리버드 3일권만 켜고 끌 수 있다")
        void savedProduct() {
            BundleProduct product = BundleProduct.restore(11L, FESTIVAL_EVENT_ID, festivalThreeDayPass());

            assertThat(product.product()).isEqualTo(TicketProduct.bundle(11L));
            assertThat(EarlyBirdScope.everything().withoutProduct(TicketProduct.singleSession()).covers(STANDING_A, product.product()))
                    .isTrue();
        }

        @Test
        @DisplayName("저장 전에는 가리킬 수 없다")
        void unsavedProduct() {
            BundleProduct product = createFestivalProduct(festivalThreeDayPass());

            assertThatIllegalStateException().isThrownBy(product::product);
        }

        @Test
        @DisplayName("되살릴 때 id는 1 이상이다")
        void restoreRequiresId() {
            assertThatIllegalArgumentException().isThrownBy(() -> BundleProduct.restore(0L, FESTIVAL_EVENT_ID, festivalThreeDayPass()));
        }
    }
}
