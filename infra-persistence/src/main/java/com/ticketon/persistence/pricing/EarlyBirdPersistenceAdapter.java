package com.ticketon.persistence.pricing;

import static com.ticketon.persistence.jooq.Tables.EARLY_BIRD;
import static com.ticketon.persistence.jooq.Tables.EARLY_BIRD_EXCLUDED_BUNDLE_PRODUCT;
import static com.ticketon.persistence.jooq.Tables.EARLY_BIRD_EXCLUDED_ZONE;

import com.ticketon.application.pricing.port.out.EarlyBirdRepository;
import com.ticketon.domain.pricing.DiscountPeriod;
import com.ticketon.domain.pricing.EarlyBird;
import com.ticketon.domain.pricing.EarlyBirdQuantity;
import com.ticketon.domain.pricing.EarlyBirdScope;
import com.ticketon.domain.pricing.EarlyBirdTerms;
import com.ticketon.domain.pricing.EarlyBirdUrgency;
import com.ticketon.domain.pricing.EarlyBirds;
import com.ticketon.domain.pricing.TicketProduct;
import com.ticketon.persistence.jooq.tables.records.EarlyBirdRecord;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 조기예매 저장소(early_bird · early_bird_excluded_zone · early_bird_excluded_bundle_product — V28) 어댑터.
 * <p>
 * 적용 범위는 끈 것만 적는다 — 1일권을 껐는지는 조기예매 줄의 칸(single_session_excluded), 끈 구역 · 묶음권 상품은 두 표의 줄이다. 고칠 때는 끈 구역
 * · 상품 줄을 지우고 지금 값으로 다시 넣는다(값의 묶음이라 줄의 정체성이 없다).
 * <p>
 * 사용 수(claimed_count)는 넣을 때 0이고 주최자의 저장은 쓰지 않는다 — 선점이 한 문장으로 늘린다(#54). 지운 조기예매는 지운 시각(deleted_at)을 적어
 * 남기고 읽지 않는다. 저장은 행사 줄을 FOR UPDATE로 잠근 흐름이 부르므로 조건 없이 지금 값으로 쓴다.
 */
@Component
public class EarlyBirdPersistenceAdapter implements EarlyBirdRepository {

    private final DSLContext dsl;

    public EarlyBirdPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public EarlyBirds findByEventId(long eventId) {
        List<EarlyBirdRecord> rows = dsl.selectFrom(EARLY_BIRD)
                .where(EARLY_BIRD.EVENT_ID.eq(eventId))
                .and(EARLY_BIRD.DELETED_AT.isNull())
                .orderBy(EARLY_BIRD.ID)
                .fetch();
        Set<Long> earlyBirdIds = rows.stream().map(EarlyBirdRecord::getId).collect(Collectors.toSet());
        Map<Long, Set<Long>> excludedZones = dsl.select(EARLY_BIRD_EXCLUDED_ZONE.EARLY_BIRD_ID, EARLY_BIRD_EXCLUDED_ZONE.ZONE_ID)
                .from(EARLY_BIRD_EXCLUDED_ZONE)
                .where(EARLY_BIRD_EXCLUDED_ZONE.EARLY_BIRD_ID.in(earlyBirdIds))
                .fetchGroups(EARLY_BIRD_EXCLUDED_ZONE.EARLY_BIRD_ID)
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> Set.copyOf(entry.getValue().getValues(EARLY_BIRD_EXCLUDED_ZONE.ZONE_ID))));
        Map<Long, Set<Long>> excludedBundleProducts = dsl.select(EARLY_BIRD_EXCLUDED_BUNDLE_PRODUCT.EARLY_BIRD_ID,
                        EARLY_BIRD_EXCLUDED_BUNDLE_PRODUCT.BUNDLE_PRODUCT_ID)
                .from(EARLY_BIRD_EXCLUDED_BUNDLE_PRODUCT)
                .where(EARLY_BIRD_EXCLUDED_BUNDLE_PRODUCT.EARLY_BIRD_ID.in(earlyBirdIds))
                .fetchGroups(EARLY_BIRD_EXCLUDED_BUNDLE_PRODUCT.EARLY_BIRD_ID)
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> Set.copyOf(entry.getValue().getValues(EARLY_BIRD_EXCLUDED_BUNDLE_PRODUCT.BUNDLE_PRODUCT_ID))));
        return EarlyBirds.of(rows.stream()
                .map(row -> toEarlyBird(row, excludedZones.getOrDefault(row.getId(), Set.of()),
                        excludedBundleProducts.getOrDefault(row.getId(), Set.of())))
                .toList());
    }

    @Override
    public EarlyBird add(EarlyBird earlyBird) {
        if (earlyBird.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 조기예매입니다: " + earlyBird.id().getAsLong());
        }
        EarlyBirdTerms terms = earlyBird.terms();
        long earlyBirdId = dsl.insertInto(EARLY_BIRD)
                .set(EARLY_BIRD.EVENT_ID, earlyBird.eventId())
                .set(EARLY_BIRD.DISCOUNT_KIND, DiscountColumns.kindOf(terms.discount()))
                .set(EARLY_BIRD.DISCOUNT_VALUE, DiscountColumns.valueOf(terms.discount()))
                .set(EARLY_BIRD.STARTS_AT, DiscountColumns.toDatabaseTime(terms.period().startsAt()))
                .set(EARLY_BIRD.ENDS_AT, DiscountColumns.toDatabaseTime(terms.period().endsAt()))
                .set(EARLY_BIRD.QUANTITY, terms.quantity().map(EarlyBirdQuantity::limit).orElse(null))
                .set(EARLY_BIRD.CLAIMED_COUNT, 0)
                .set(EARLY_BIRD.HIGHEST_URGENCY_REACHED, earlyBird.highestUrgencyReached().name())
                .set(EARLY_BIRD.SINGLE_SESSION_EXCLUDED, singleSessionExcluded(terms.scope()))
                .returningResult(EARLY_BIRD.ID)
                .fetchSingle()
                .value1();
        insertScope(earlyBird.eventId(), earlyBirdId, terms.scope());
        return EarlyBird.restore(earlyBirdId, earlyBird.eventId(), terms, 0, earlyBird.highestUrgencyReached());
    }

    @Override
    public void update(EarlyBird earlyBird) {
        long earlyBirdId = earlyBird.id().orElseThrow(() -> new IllegalArgumentException("저장 전 조기예매는 고쳐 쓸 수 없습니다"));
        EarlyBirdTerms terms = earlyBird.terms();
        int updated = dsl.update(EARLY_BIRD)
                .set(EARLY_BIRD.DISCOUNT_KIND, DiscountColumns.kindOf(terms.discount()))
                .set(EARLY_BIRD.DISCOUNT_VALUE, DiscountColumns.valueOf(terms.discount()))
                .set(EARLY_BIRD.STARTS_AT, DiscountColumns.toDatabaseTime(terms.period().startsAt()))
                .set(EARLY_BIRD.ENDS_AT, DiscountColumns.toDatabaseTime(terms.period().endsAt()))
                .set(EARLY_BIRD.QUANTITY, terms.quantity().map(EarlyBirdQuantity::limit).orElse(null))
                .set(EARLY_BIRD.HIGHEST_URGENCY_REACHED, earlyBird.highestUrgencyReached().name())
                .set(EARLY_BIRD.SINGLE_SESSION_EXCLUDED, singleSessionExcluded(terms.scope()))
                .where(EARLY_BIRD.ID.eq(earlyBirdId))
                .and(EARLY_BIRD.EVENT_ID.eq(earlyBird.eventId()))
                .and(EARLY_BIRD.DELETED_AT.isNull())
                .execute();
        if (updated == 0) {
            throw new IllegalArgumentException("표에 없거나 지운 조기예매입니다: 행사 " + earlyBird.eventId() + ", 조기예매 " + earlyBirdId);
        }
        dsl.deleteFrom(EARLY_BIRD_EXCLUDED_ZONE).where(EARLY_BIRD_EXCLUDED_ZONE.EARLY_BIRD_ID.eq(earlyBirdId)).execute();
        dsl.deleteFrom(EARLY_BIRD_EXCLUDED_BUNDLE_PRODUCT).where(EARLY_BIRD_EXCLUDED_BUNDLE_PRODUCT.EARLY_BIRD_ID.eq(earlyBirdId)).execute();
        insertScope(earlyBird.eventId(), earlyBirdId, terms.scope());
    }

    @Override
    public void remove(long eventId, long earlyBirdId, Instant now) {
        int removed = dsl.update(EARLY_BIRD)
                .set(EARLY_BIRD.DELETED_AT, DiscountColumns.toDatabaseTime(now))
                .where(EARLY_BIRD.ID.eq(earlyBirdId))
                .and(EARLY_BIRD.EVENT_ID.eq(eventId))
                .and(EARLY_BIRD.DELETED_AT.isNull())
                .execute();
        if (removed == 0) {
            throw new IllegalArgumentException("표에 없거나 지운 조기예매입니다: 행사 " + eventId + ", 조기예매 " + earlyBirdId);
        }
    }

    private void insertScope(long eventId, long earlyBirdId, EarlyBirdScope scope) {
        for (long zoneId : scope.excludedZoneIds()) {
            dsl.insertInto(EARLY_BIRD_EXCLUDED_ZONE)
                    .set(EARLY_BIRD_EXCLUDED_ZONE.EARLY_BIRD_ID, earlyBirdId)
                    .set(EARLY_BIRD_EXCLUDED_ZONE.EVENT_ID, eventId)
                    .set(EARLY_BIRD_EXCLUDED_ZONE.ZONE_ID, zoneId)
                    .execute();
        }
        for (TicketProduct product : scope.excludedProducts()) {
            if (product instanceof TicketProduct.Bundle bundle) {
                dsl.insertInto(EARLY_BIRD_EXCLUDED_BUNDLE_PRODUCT)
                        .set(EARLY_BIRD_EXCLUDED_BUNDLE_PRODUCT.EARLY_BIRD_ID, earlyBirdId)
                        .set(EARLY_BIRD_EXCLUDED_BUNDLE_PRODUCT.EVENT_ID, eventId)
                        .set(EARLY_BIRD_EXCLUDED_BUNDLE_PRODUCT.BUNDLE_PRODUCT_ID, bundle.bundleProductId())
                        .execute();
            }
        }
    }

    private static boolean singleSessionExcluded(EarlyBirdScope scope) {
        return scope.excludedProducts().contains(TicketProduct.singleSession());
    }

    private static EarlyBird toEarlyBird(EarlyBirdRecord row, Set<Long> excludedZoneIds, Set<Long> excludedBundleProductIds) {
        Set<TicketProduct> excludedProducts = new HashSet<>();
        if (row.getSingleSessionExcluded()) {
            excludedProducts.add(TicketProduct.singleSession());
        }
        excludedBundleProductIds.forEach(bundleProductId -> excludedProducts.add(TicketProduct.bundle(bundleProductId)));
        EarlyBirdTerms terms = new EarlyBirdTerms(
                DiscountColumns.toDiscount(row.getDiscountKind(), row.getDiscountValue()),
                DiscountPeriod.of(row.getStartsAt().toInstant(), row.getEndsAt().toInstant()),
                Optional.ofNullable(row.getQuantity()).map(EarlyBirdQuantity::of),
                new EarlyBirdScope(excludedZoneIds, excludedProducts));
        return EarlyBird.restore(row.getId(), row.getEventId(), terms, row.getClaimedCount(),
                EarlyBirdUrgency.valueOf(row.getHighestUrgencyReached()));
    }
}
