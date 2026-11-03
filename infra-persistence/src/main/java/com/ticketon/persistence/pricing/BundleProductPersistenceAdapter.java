package com.ticketon.persistence.pricing;

import static com.ticketon.persistence.jooq.Tables.BUNDLE_PRODUCT;
import static com.ticketon.persistence.jooq.Tables.BUNDLE_PRODUCT_DATE;
import static com.ticketon.persistence.jooq.Tables.BUNDLE_PRODUCT_ZONE;

import com.ticketon.application.pricing.port.out.BundleProductRepository;
import com.ticketon.domain.policy.Percent;
import com.ticketon.domain.pricing.BundleProduct;
import com.ticketon.domain.pricing.BundleProductName;
import com.ticketon.domain.pricing.BundleProductTerms;
import com.ticketon.persistence.jooq.tables.records.BundleProductRecord;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 묶음권 상품 저장소(bundle_product · bundle_product_date · bundle_product_zone — V28) 어댑터.
 * <p>
 * 묶는 날짜와 파는 구역은 값의 묶음이라 고칠 때 줄을 지우고 지금 값으로 다시 넣는다. 지운 상품은 지운 시각(deleted_at)을 적어 남기고 읽지 않는다 —
 * 날짜 · 구역 줄도 그대로 둔다. 저장은 행사 줄을 FOR UPDATE로 잠근 흐름이 부르므로 조건 없이 지금 값으로 쓴다.
 */
@Component
public class BundleProductPersistenceAdapter implements BundleProductRepository {

    private final DSLContext dsl;

    public BundleProductPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<BundleProduct> findByEventId(long eventId) {
        List<BundleProductRecord> rows = dsl.selectFrom(BUNDLE_PRODUCT)
                .where(BUNDLE_PRODUCT.EVENT_ID.eq(eventId))
                .and(BUNDLE_PRODUCT.DELETED_AT.isNull())
                .orderBy(BUNDLE_PRODUCT.ID)
                .fetch();
        Set<Long> productIds = rows.stream().map(BundleProductRecord::getId).collect(Collectors.toSet());
        Map<Long, Set<Long>> dateIds = dsl.select(BUNDLE_PRODUCT_DATE.BUNDLE_PRODUCT_ID, BUNDLE_PRODUCT_DATE.DATE_ID)
                .from(BUNDLE_PRODUCT_DATE)
                .where(BUNDLE_PRODUCT_DATE.BUNDLE_PRODUCT_ID.in(productIds))
                .fetchGroups(BUNDLE_PRODUCT_DATE.BUNDLE_PRODUCT_ID)
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue().getValues(BUNDLE_PRODUCT_DATE.DATE_ID))));
        Map<Long, Set<Long>> zoneIds = dsl.select(BUNDLE_PRODUCT_ZONE.BUNDLE_PRODUCT_ID, BUNDLE_PRODUCT_ZONE.ZONE_ID)
                .from(BUNDLE_PRODUCT_ZONE)
                .where(BUNDLE_PRODUCT_ZONE.BUNDLE_PRODUCT_ID.in(productIds))
                .fetchGroups(BUNDLE_PRODUCT_ZONE.BUNDLE_PRODUCT_ID)
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue().getValues(BUNDLE_PRODUCT_ZONE.ZONE_ID))));
        return rows.stream()
                .map(row -> BundleProduct.restore(row.getId(), row.getEventId(), new BundleProductTerms(BundleProductName.of(row.getName()),
                        dateIds.getOrDefault(row.getId(), Set.of()), zoneIds.getOrDefault(row.getId(), Set.of()),
                        Percent.of(row.getDiscountRate()))))
                .toList();
    }

    @Override
    public BundleProduct add(BundleProduct bundleProduct) {
        if (bundleProduct.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 묶음권 상품입니다: " + bundleProduct.id().getAsLong());
        }
        BundleProductTerms terms = bundleProduct.terms();
        long productId = dsl.insertInto(BUNDLE_PRODUCT)
                .set(BUNDLE_PRODUCT.EVENT_ID, bundleProduct.eventId())
                .set(BUNDLE_PRODUCT.NAME, terms.name().value())
                .set(BUNDLE_PRODUCT.DISCOUNT_RATE, terms.discountRate().value())
                .returningResult(BUNDLE_PRODUCT.ID)
                .fetchSingle()
                .value1();
        insertDatesAndZones(bundleProduct.eventId(), productId, terms);
        return BundleProduct.restore(productId, bundleProduct.eventId(), terms);
    }

    @Override
    public void update(BundleProduct bundleProduct) {
        long productId = bundleProduct.id().orElseThrow(() -> new IllegalArgumentException("저장 전 묶음권 상품은 고쳐 쓸 수 없습니다"));
        BundleProductTerms terms = bundleProduct.terms();
        int updated = dsl.update(BUNDLE_PRODUCT)
                .set(BUNDLE_PRODUCT.NAME, terms.name().value())
                .set(BUNDLE_PRODUCT.DISCOUNT_RATE, terms.discountRate().value())
                .where(BUNDLE_PRODUCT.ID.eq(productId))
                .and(BUNDLE_PRODUCT.EVENT_ID.eq(bundleProduct.eventId()))
                .and(BUNDLE_PRODUCT.DELETED_AT.isNull())
                .execute();
        if (updated == 0) {
            throw new IllegalArgumentException("표에 없거나 지운 묶음권 상품입니다: 행사 " + bundleProduct.eventId() + ", 상품 " + productId);
        }
        dsl.deleteFrom(BUNDLE_PRODUCT_DATE).where(BUNDLE_PRODUCT_DATE.BUNDLE_PRODUCT_ID.eq(productId)).execute();
        dsl.deleteFrom(BUNDLE_PRODUCT_ZONE).where(BUNDLE_PRODUCT_ZONE.BUNDLE_PRODUCT_ID.eq(productId)).execute();
        insertDatesAndZones(bundleProduct.eventId(), productId, terms);
    }

    @Override
    public void remove(long eventId, long bundleProductId, Instant now) {
        int removed = dsl.update(BUNDLE_PRODUCT)
                .set(BUNDLE_PRODUCT.DELETED_AT, DiscountColumns.toDatabaseTime(now))
                .where(BUNDLE_PRODUCT.ID.eq(bundleProductId))
                .and(BUNDLE_PRODUCT.EVENT_ID.eq(eventId))
                .and(BUNDLE_PRODUCT.DELETED_AT.isNull())
                .execute();
        if (removed == 0) {
            throw new IllegalArgumentException("표에 없거나 지운 묶음권 상품입니다: 행사 " + eventId + ", 상품 " + bundleProductId);
        }
    }

    private void insertDatesAndZones(long eventId, long productId, BundleProductTerms terms) {
        for (long dateId : terms.dateIds()) {
            dsl.insertInto(BUNDLE_PRODUCT_DATE)
                    .set(BUNDLE_PRODUCT_DATE.BUNDLE_PRODUCT_ID, productId)
                    .set(BUNDLE_PRODUCT_DATE.EVENT_ID, eventId)
                    .set(BUNDLE_PRODUCT_DATE.DATE_ID, dateId)
                    .execute();
        }
        for (long zoneId : terms.zoneIds()) {
            dsl.insertInto(BUNDLE_PRODUCT_ZONE)
                    .set(BUNDLE_PRODUCT_ZONE.BUNDLE_PRODUCT_ID, productId)
                    .set(BUNDLE_PRODUCT_ZONE.EVENT_ID, eventId)
                    .set(BUNDLE_PRODUCT_ZONE.ZONE_ID, zoneId)
                    .execute();
        }
    }
}
