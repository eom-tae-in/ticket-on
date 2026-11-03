package com.ticketon.application.pricing.port.in;

import java.util.List;
import java.util.Objects;

/**
 * 묶음권 상품 한 줄 — 위저드 4단계의 묶음권 목록이 보이는 칸(docs/05 "조합은 자동 생성하지 않습니다 — 묶음권 만들기").
 *
 * @param bundleProductId 묶음권 상품 id
 * @param name            이름
 * @param dateIds         묶는 날짜 — id 순. 일정에서 지워진 날짜를 가리킬 수 있다(그때는 고를 회차가 없어 팔리지 않는다)
 * @param zoneIds         파는 구역 — id 순
 * @param discountRate    할인율(%)
 * @param startingPrice   시작 가격(원) — 가장 싼 회차 조합의 묶음권 가격("3일권 40,000원~"). 한 날짜라도 고를 회차가 없으면 null
 */
public record BundleProductResult(long bundleProductId, String name, List<Long> dateIds, List<Long> zoneIds, int discountRate,
        Long startingPrice) {

    public BundleProductResult {
        Objects.requireNonNull(name, "name");
        dateIds = List.copyOf(dateIds);
        zoneIds = List.copyOf(zoneIds);
    }
}
