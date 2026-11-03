package com.ticketon.persistence.pricing;

import static com.ticketon.persistence.jooq.Tables.BUNDLE_PRODUCT_DATE;
import static com.ticketon.persistence.jooq.Tables.COUPON;
import static com.ticketon.persistence.jooq.Tables.EARLY_BIRD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.pricing.BundleProduct;
import com.ticketon.domain.pricing.BundleProductName;
import com.ticketon.domain.pricing.BundleProductTerms;
import com.ticketon.domain.pricing.Coupon;
import com.ticketon.domain.pricing.CouponCode;
import com.ticketon.domain.pricing.CouponTerms;
import com.ticketon.domain.pricing.Discount;
import com.ticketon.domain.pricing.DiscountPeriod;
import com.ticketon.domain.pricing.EarlyBird;
import com.ticketon.domain.pricing.EarlyBirdQuantity;
import com.ticketon.domain.pricing.EarlyBirdScope;
import com.ticketon.domain.pricing.EarlyBirdTerms;
import com.ticketon.domain.pricing.EarlyBirdUrgency;
import com.ticketon.domain.pricing.Money;
import com.ticketon.domain.pricing.TicketProduct;
import com.ticketon.persistence.event.EventSchedulePersistenceAdapter;
import com.ticketon.persistence.event.FloorPlanPersistenceAdapter;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL에 마이그레이션(V28 가격)을 올리고 묶음권 상품 · 조기예매 · 쿠폰 저장을 본다 — 새 줄 id, 다시 읽기(만든 순서), 고쳐 쓰기(사용 수 ·
 * 코드는 쓰지 않음), 지운 시각을 적어 남기기, 행사끼리 나뉘는지, 지우지 않은 쿠폰끼리의 코드 유일 색인. 늦여름(docs/15 이도현)의 페스티벌 — 1일권 ·
 * 3일권 7%, 얼리버드, 팬 쿠폰. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("묶음권 상품 · 조기예매 · 쿠폰 저장소 (진짜 PostgreSQL)")
class PricingPersistenceAdapterTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final String UNIQUE_VIOLATION = "23505";
    private static final Instant REMOVED_AT = Instant.parse("2026-04-02T03:00:00Z");
    private static final DiscountPeriod APRIL = DiscountPeriod.of(Instant.parse("2026-03-31T15:00:00Z"), Instant.parse("2026-04-19T15:00:00Z"));
    private static final DiscountPeriod MAY = DiscountPeriod.of(Instant.parse("2026-04-30T15:00:00Z"), Instant.parse("2026-05-02T15:00:00Z"));

    private static CloseableDSLContext dsl;

    private long festival;
    private long otherFestival;
    private long may3;
    private long may4;
    private long may5;
    private long standingA;
    private long standingB;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @AfterAll
    static void close() {
        dsl.close();
    }

    @BeforeEach
    void storeFestival() {
        PricingRows rows = new PricingRows(dsl);
        long lateSummer = rows.freshOrganizer();
        festival = rows.festival(lateSummer, "2026 늦여름 페스티벌");
        otherFestival = rows.festival(lateSummer, "2026 늦가을 페스티벌");
        rows.allDaySchedule(festival);
        may3 = rows.dateWithSession(festival, LocalDate.of(2026, 5, 3));
        may4 = rows.dateWithSession(festival, LocalDate.of(2026, 5, 4));
        may5 = rows.dateWithSession(festival, LocalDate.of(2026, 5, 5));
        standingA = rows.quantityZone(festival, "스탠딩A", 35_000);
        standingB = rows.quantityZone(festival, "스탠딩B", 30_000);
    }

    /** 저장 전 묶음권 상품 — 표에 넣은 일정 · 도면을 저장소로 읽어 도메인의 만들기 규칙을 거친다. */
    private BundleProduct unsaved(BundleProductTerms terms) {
        return BundleProduct.create(festival, terms, new EventSchedulePersistenceAdapter(dsl).findByEventId(festival).orElseThrow(),
                new FloorPlanPersistenceAdapter(dsl).findByEventId(festival));
    }

    @Nested
    @DisplayName("묶음권 상품")
    class BundleProductStore {

        private final BundleProductPersistenceAdapter adapter = new BundleProductPersistenceAdapter(dsl);

        private BundleProductTerms threeDayPass() {
            return new BundleProductTerms(BundleProductName.of("3일 통권"), Set.of(may3, may4, may5), Set.of(standingA), Percent.of(7));
        }

        @Test
        @DisplayName("3일 통권(5/3 · 5/4 · 5/5, 스탠딩A, 7%)을 넣으면 id가 붙고, 다시 읽으면 그대로다")
        void addsAndReads() {
            BundleProduct added = adapter.add(unsaved(threeDayPass()));

            assertThat(added.id()).isPresent();
            assertThat(adapter.findByEventId(festival)).singleElement().satisfies(found -> {
                assertThat(found.id()).isEqualTo(added.id());
                assertThat(found.terms()).isEqualTo(threeDayPass());
            });
            assertThat(adapter.findByEventId(otherFestival)).isEmpty();
        }

        @Test
        @DisplayName("이름 · 날짜 · 구역 · 할인율을 고쳐 쓰면 바뀐 값이 읽힌다")
        void updates() {
            BundleProduct added = adapter.add(unsaved(threeDayPass()));
            BundleProduct changed = BundleProduct.restore(added.id().getAsLong(), festival, new BundleProductTerms(
                    BundleProductName.of("주말 2일권"), Set.of(may4, may5), Set.of(standingA, standingB), Percent.of(3)));

            adapter.update(changed);

            assertThat(adapter.findByEventId(festival)).singleElement().extracting(BundleProduct::terms).isEqualTo(changed.terms());
        }

        @Test
        @DisplayName("지우면 줄은 지운 시각과 함께 남고 읽히지 않는다 — 날짜 줄도 남는다. 다시 지우거나 고쳐 쓰면 거부한다")
        void removesKeepingRow() {
            BundleProduct added = adapter.add(unsaved(threeDayPass()));
            long productId = added.id().getAsLong();

            adapter.remove(festival, productId, REMOVED_AT);

            assertThat(adapter.findByEventId(festival)).isEmpty();
            assertThat(dsl.fetchCount(BUNDLE_PRODUCT_DATE, BUNDLE_PRODUCT_DATE.BUNDLE_PRODUCT_ID.eq(productId))).isEqualTo(3);
            assertThatIllegalArgumentException().isThrownBy(() -> adapter.remove(festival, productId, REMOVED_AT));
            assertThatIllegalArgumentException().isThrownBy(() -> adapter.update(added));
        }

        @Test
        @DisplayName("다른 행사의 상품은 그 행사 id로 지울 수 없다")
        void otherEventCannotRemove() {
            long productId = adapter.add(unsaved(threeDayPass())).id().getAsLong();

            assertThatIllegalArgumentException().isThrownBy(() -> adapter.remove(otherFestival, productId, REMOVED_AT));
        }

    }

    @Nested
    @DisplayName("조기예매")
    class EarlyBirdStore {

        private final EarlyBirdPersistenceAdapter adapter = new EarlyBirdPersistenceAdapter(dsl);

        /** 1차 얼리버드 — 3일권만 30% · 4월 · 30장, 스탠딩B 끔. */
        private EarlyBirdTerms firstEarlyBird(long threeDayPassId) {
            return EarlyBirdTerms.limited(Discount.rate(Percent.of(30)), APRIL, EarlyBirdQuantity.of(30), EarlyBirdScope.everything()
                    .withoutProduct(TicketProduct.singleSession()).withoutZone(standingB));
        }

        private long threeDayPassId() {
            return new BundleProductPersistenceAdapter(dsl).add(unsaved(new BundleProductTerms(BundleProductName.of("3일 통권"),
                    Set.of(may3, may4, may5), Set.of(standingA), Percent.of(7)))).id().getAsLong();
        }

        @Test
        @DisplayName("1차(30%, 30장, 1일권 · 스탠딩B 끔) · 2차(정액 5,000원, 기간만, 3일권 끔)를 넣으면 만든 순서로 그대로 읽힌다 — 사용 수 0")
        void addsAndReadsInOrder() {
            long threeDayPass = threeDayPassId();
            EarlyBird first = adapter.add(EarlyBird.create(festival, firstEarlyBird(threeDayPass)));
            EarlyBirdTerms secondTerms = EarlyBirdTerms.periodOnly(Discount.fixedAmount(Money.of(5_000)), MAY,
                    EarlyBirdScope.everything().withoutProduct(TicketProduct.bundle(threeDayPass)));
            EarlyBird second = adapter.add(EarlyBird.create(festival, secondTerms));

            List<EarlyBird> found = adapter.findByEventId(festival).all();

            assertThat(found).extracting(earlyBird -> earlyBird.id().getAsLong())
                    .containsExactly(first.id().getAsLong(), second.id().getAsLong());
            assertThat(found.get(0).terms()).isEqualTo(firstEarlyBird(threeDayPass));
            assertThat(found.get(1).terms()).isEqualTo(secondTerms);
            assertThat(found).allSatisfy(earlyBird -> {
                assertThat(earlyBird.claimedCount()).isZero();
                assertThat(earlyBird.highestUrgencyReached()).isEqualTo(EarlyBirdUrgency.NONE);
            });
            assertThat(adapter.findByEventId(otherFestival).all()).isEmpty();
        }

        @Test
        @DisplayName("고쳐 쓰면 설정 · 도달한 임박 단계가 바뀌고, 사용 수는 표의 값 그대로다 — 선점이 늘린 몫을 줄이지 않는다")
        void updateKeepsClaimedCount() {
            EarlyBird added = adapter.add(EarlyBird.create(festival, firstEarlyBird(threeDayPassId())));
            long earlyBirdId = added.id().getAsLong();
            dsl.update(EARLY_BIRD).set(EARLY_BIRD.CLAIMED_COUNT, 12).where(EARLY_BIRD.ID.eq(earlyBirdId)).execute();
            EarlyBirdTerms widened = EarlyBirdTerms.limited(Discount.rate(Percent.of(20)), APRIL, EarlyBirdQuantity.of(50),
                    EarlyBirdScope.everything());

            adapter.update(EarlyBird.restore(earlyBirdId, festival, widened, 0, EarlyBirdUrgency.RUNNING_LOW));

            EarlyBird found = adapter.findByEventId(festival).all().getFirst();
            assertThat(found.terms()).isEqualTo(widened);
            assertThat(found.claimedCount()).isEqualTo(12);
            assertThat(found.highestUrgencyReached()).isEqualTo(EarlyBirdUrgency.RUNNING_LOW);
        }

        @Test
        @DisplayName("지우면 줄은 남고 읽히지 않는다. 다시 지우거나 고쳐 쓰면 거부한다")
        void removesKeepingRow() {
            EarlyBird added = adapter.add(EarlyBird.create(festival, firstEarlyBird(threeDayPassId())));
            long earlyBirdId = added.id().getAsLong();

            adapter.remove(festival, earlyBirdId, REMOVED_AT);

            assertThat(adapter.findByEventId(festival).all()).isEmpty();
            assertThat(dsl.fetchCount(EARLY_BIRD, EARLY_BIRD.ID.eq(earlyBirdId).and(EARLY_BIRD.DELETED_AT.isNotNull()))).isEqualTo(1);
            assertThatIllegalArgumentException().isThrownBy(() -> adapter.remove(festival, earlyBirdId, REMOVED_AT));
            assertThatIllegalArgumentException().isThrownBy(() -> adapter.update(added));
        }

        @Test
        @DisplayName("다른 행사의 구역을 끈 범위는 표가 받지 않는다 — 같은 행사의 구역만 가리킨다")
        void otherEventZoneRejected() {
            long otherZone = new PricingRows(dsl).quantityZone(otherFestival, "스탠딩A", 35_000);

            assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> adapter.add(EarlyBird.create(festival,
                    EarlyBirdTerms.periodOnly(Discount.rate(Percent.of(10)), APRIL, EarlyBirdScope.everything().withoutZone(otherZone)))));
        }
    }

    @Nested
    @DisplayName("쿠폰")
    class CouponStore {

        private final CouponPersistenceAdapter adapter = new CouponPersistenceAdapter(dsl);

        private Coupon fanCoupon(String code) {
            return Coupon.issue(festival, CouponCode.of(code), new CouponTerms(Discount.fixedAmount(Money.of(5_000)), 100, true, APRIL));
        }

        @Test
        @DisplayName("팬 쿠폰을 발급하면 id가 붙고 대문자로 맞춘 코드 그대로 읽힌다 — 사용 수 0")
        void addsAndReads() {
            Coupon added = adapter.add(fanCoupon("fan5000"));

            assertThat(adapter.findByEventId(festival)).singleElement().satisfies(found -> {
                assertThat(found.id()).isEqualTo(added.id());
                assertThat(found.code().value()).isEqualTo("FAN5000");
                assertThat(found.terms()).isEqualTo(added.terms());
                assertThat(found.usedCount()).isZero();
            });
            assertThat(dsl.fetchValue(COUPON.CODE, COUPON.ID.eq(added.id().getAsLong()))).isEqualTo("FAN5000");
        }

        @Test
        @DisplayName("고쳐 쓰면 설정이 바뀌고, 사용 수는 표의 값 그대로다")
        void updateKeepsUsedCount() {
            Coupon added = adapter.add(fanCoupon("FAN5000"));
            long couponId = added.id().getAsLong();
            dsl.update(COUPON).set(COUPON.USED_COUNT, 7).where(COUPON.ID.eq(couponId)).execute();
            CouponTerms changed = new CouponTerms(Discount.rate(Percent.of(20)), 50, false, MAY);

            adapter.update(Coupon.restore(couponId, festival, CouponCode.of("FAN5000"), changed, 0));

            Coupon found = adapter.findByEventId(festival).getFirst();
            assertThat(found.terms()).isEqualTo(changed);
            assertThat(found.usedCount()).isEqualTo(7);
        }

        @Test
        @DisplayName("지우지 않은 쿠폰끼리는 같은 코드를 표가 받지 않고, 지운 쿠폰의 코드는 다시 쓴다 — 다른 행사는 같은 코드를 쓴다")
        void codeUniqueAmongLiveCoupons() {
            long couponId = adapter.add(fanCoupon("SPRING20")).id().getAsLong();

            assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> adapter.add(fanCoupon("spring20")))
                    .satisfies(rejected -> assertThat(rejected.sqlState()).isEqualTo(UNIQUE_VIOLATION));
            adapter.add(Coupon.issue(otherFestival, CouponCode.of("SPRING20"), fanCoupon("SPRING20").terms()));

            adapter.remove(festival, couponId, REMOVED_AT);
            Coupon reissued = adapter.add(fanCoupon("SPRING20"));

            assertThat(adapter.findByEventId(festival)).extracting(coupon -> coupon.id().getAsLong())
                    .containsExactly(reissued.id().getAsLong());
            assertThatIllegalArgumentException().isThrownBy(() -> adapter.remove(festival, couponId, REMOVED_AT));
        }
    }
}
