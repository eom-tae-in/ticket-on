package com.ticketon.domain.pricing;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 조기예매 적용 범위 — <b>기본값은 전체 구역 · 전체 상품</b>이고, 구역별 · 상품별로 끈다(docs/05 "조기예매 — 적용 범위"). 좌석 번호까지 특정하지는
 * 않는다.
 * <p>
 * 끈 것만 적어 둔다 — 기본값이 전체라, 구역이나 묶음권 상품을 나중에 더해도 저절로 범위에 들어간다. "얼리버드 3일권만"은 1일권을 끄고, "1일권만
 * 얼리버드"는 묶음권 상품을 모두 끈다(docs/05 "조기예매·쿠폰과 함께 씁니다").
 * <p>
 * 모든 구역이나 모든 상품을 꺼서 아무 데도 닿지 않는 범위인지는 행사의 구역 · 상품 목록을 아는 쪽이 본다.
 *
 * @param excludedZoneIds  끈 구역의 id. 각각 1 이상
 * @param excludedProducts 끈 상품
 */
public record EarlyBirdScope(Set<Long> excludedZoneIds, Set<TicketProduct> excludedProducts) {

    private static final EarlyBirdScope EVERYTHING = new EarlyBirdScope(Set.of(), Set.of());

    public EarlyBirdScope {
        excludedZoneIds = Set.copyOf(excludedZoneIds);
        excludedProducts = Set.copyOf(excludedProducts);
        for (long zoneId : excludedZoneIds) {
            if (zoneId < 1) {
                throw new IllegalArgumentException("구역 id는 1 이상이어야 합니다: " + zoneId);
            }
        }
    }

    /** 기본값 — 전체 구역 · 전체 상품. */
    public static EarlyBirdScope everything() {
        return EVERYTHING;
    }

    /** 이 범위에서 그 구역을 끈 범위. */
    public EarlyBirdScope withoutZone(long zoneId) {
        return new EarlyBirdScope(union(excludedZoneIds, zoneId), excludedProducts);
    }

    /** 이 범위에서 그 상품을 끈 범위. */
    public EarlyBirdScope withoutProduct(TicketProduct product) {
        Objects.requireNonNull(product, "product");
        return new EarlyBirdScope(excludedZoneIds, union(excludedProducts, product));
    }

    /** 그 구역의 그 상품 티켓에 이 조기예매가 닿는지 — 구역도 상품도 끄지 않았다. */
    public boolean covers(long zoneId, TicketProduct product) {
        Objects.requireNonNull(product, "product");
        return !excludedZoneIds.contains(zoneId) && !excludedProducts.contains(product);
    }

    private static <T> Set<T> union(Set<T> values, T added) {
        Set<T> merged = new HashSet<>(values);
        merged.add(added);
        return merged;
    }
}
