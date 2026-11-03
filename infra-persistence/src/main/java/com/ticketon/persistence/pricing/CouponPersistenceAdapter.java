package com.ticketon.persistence.pricing;

import static com.ticketon.persistence.jooq.Tables.COUPON;

import com.ticketon.application.pricing.port.out.CouponRepository;
import com.ticketon.domain.pricing.Coupon;
import com.ticketon.domain.pricing.CouponCode;
import com.ticketon.domain.pricing.CouponTerms;
import com.ticketon.domain.pricing.DiscountPeriod;
import com.ticketon.persistence.jooq.tables.records.CouponRecord;
import java.time.Instant;
import java.util.List;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 쿠폰 저장소(coupon — V28) 어댑터.
 * <p>
 * 코드는 도메인이 대문자로 맞춘 값 그대로 저장한다 — 대소문자만 다른 코드는 같은 값이라 지우지 않은 쿠폰끼리의 유일 색인(coupon_event_code_ux)이
 * 마지막으로 막는다. 코드와 사용 수(used_count)는 넣을 때만 쓴다 — 코드는 바꾸지 않고, 사용 수는 예매가 한 문장으로 늘린다(#54 · #73). 지운 쿠폰은
 * 지운 시각(deleted_at)을 적어 남기고 읽지 않는다. 저장은 행사 줄을 FOR UPDATE로 잠근 흐름이 부르므로 조건 없이 지금 값으로 쓴다.
 */
@Component
public class CouponPersistenceAdapter implements CouponRepository {

    private final DSLContext dsl;

    public CouponPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<Coupon> findByEventId(long eventId) {
        return dsl.selectFrom(COUPON)
                .where(COUPON.EVENT_ID.eq(eventId))
                .and(COUPON.DELETED_AT.isNull())
                .orderBy(COUPON.ID)
                .fetch(CouponPersistenceAdapter::toCoupon);
    }

    @Override
    public Coupon add(Coupon coupon) {
        if (coupon.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 쿠폰입니다: " + coupon.id().getAsLong());
        }
        CouponTerms terms = coupon.terms();
        long couponId = dsl.insertInto(COUPON)
                .set(COUPON.EVENT_ID, coupon.eventId())
                .set(COUPON.CODE, coupon.code().value())
                .set(COUPON.DISCOUNT_KIND, DiscountColumns.kindOf(terms.discount()))
                .set(COUPON.DISCOUNT_VALUE, DiscountColumns.valueOf(terms.discount()))
                .set(COUPON.USAGE_LIMIT, terms.usageLimit())
                .set(COUPON.USED_COUNT, 0)
                .set(COUPON.ONE_PER_PERSON, terms.onePerPerson())
                .set(COUPON.VALID_FROM, DiscountColumns.toDatabaseTime(terms.validity().startsAt()))
                .set(COUPON.VALID_UNTIL, DiscountColumns.toDatabaseTime(terms.validity().endsAt()))
                .returningResult(COUPON.ID)
                .fetchSingle()
                .value1();
        return Coupon.restore(couponId, coupon.eventId(), coupon.code(), terms, 0);
    }

    @Override
    public void update(Coupon coupon) {
        long couponId = coupon.id().orElseThrow(() -> new IllegalArgumentException("저장 전 쿠폰은 고쳐 쓸 수 없습니다"));
        CouponTerms terms = coupon.terms();
        int updated = dsl.update(COUPON)
                .set(COUPON.DISCOUNT_KIND, DiscountColumns.kindOf(terms.discount()))
                .set(COUPON.DISCOUNT_VALUE, DiscountColumns.valueOf(terms.discount()))
                .set(COUPON.USAGE_LIMIT, terms.usageLimit())
                .set(COUPON.ONE_PER_PERSON, terms.onePerPerson())
                .set(COUPON.VALID_FROM, DiscountColumns.toDatabaseTime(terms.validity().startsAt()))
                .set(COUPON.VALID_UNTIL, DiscountColumns.toDatabaseTime(terms.validity().endsAt()))
                .where(COUPON.ID.eq(couponId))
                .and(COUPON.EVENT_ID.eq(coupon.eventId()))
                .and(COUPON.DELETED_AT.isNull())
                .execute();
        if (updated == 0) {
            throw new IllegalArgumentException("표에 없거나 지운 쿠폰입니다: 행사 " + coupon.eventId() + ", 쿠폰 " + couponId);
        }
    }

    @Override
    public void remove(long eventId, long couponId, Instant now) {
        int removed = dsl.update(COUPON)
                .set(COUPON.DELETED_AT, DiscountColumns.toDatabaseTime(now))
                .where(COUPON.ID.eq(couponId))
                .and(COUPON.EVENT_ID.eq(eventId))
                .and(COUPON.DELETED_AT.isNull())
                .execute();
        if (removed == 0) {
            throw new IllegalArgumentException("표에 없거나 지운 쿠폰입니다: 행사 " + eventId + ", 쿠폰 " + couponId);
        }
    }

    private static Coupon toCoupon(CouponRecord row) {
        CouponTerms terms = new CouponTerms(DiscountColumns.toDiscount(row.getDiscountKind(), row.getDiscountValue()), row.getUsageLimit(),
                row.getOnePerPerson(), DiscountPeriod.of(row.getValidFrom().toInstant(), row.getValidUntil().toInstant()));
        return Coupon.restore(row.getId(), row.getEventId(), CouponCode.of(row.getCode()), terms, row.getUsedCount());
    }
}
