package com.ticketon.domain.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 묶음권 판매 가능 여부 — 묶는 날짜마다의 회차 재고로 판정한다(docs/05 "재고가 엮입니다").
 * <blockquote>5/3 정원 500 · 5/4 정원 500 · 5/5 정원 500. 통권 1장이 팔리면 → 세 회차가 각각 499</blockquote>
 * <b>한 날짜라도 들어갈 회차가 없으면(그 날짜의 회차가 모두 매진이면) 그 묶음권은 판매가 멈춘다.</b> 3일권은 세 날짜 모두 들어가야 쓸 수 있다.
 * 멈춤도 매진처럼 상태가 아니라 계산이다 — 취소표가 나와 그 날짜에 자리가 생기면 다시 판다(docs/04 "'매진'은 상태가 아닙니다").
 * <p>
 * 묶음권 한 장은 날짜마다 관객이 고른 회차 하나씩을 가리키고, 그 회차마다 1씩 잡는다({@link QuantityZoneStock#hold}). 묶음권은 수량형 구역에서만
 * 판다(docs/05 "왜 수량형 전용인가") — 묶음권 상품(#50)이 그 규칙과 날짜 · 구역을 갖고, 여기서는 날짜별 회차 재고만 받아 판정한다.
 */
public final class BundleAvailability {

    private final List<BundleDateStock> dates;

    private BundleAvailability(List<BundleDateStock> dates) {
        this.dates = List.copyOf(Objects.requireNonNull(dates, "dates"));
        if (this.dates.isEmpty()) {
            throw new IllegalArgumentException("묶음권은 날짜를 하나 이상 묶어야 합니다");
        }
        if (this.dates.stream().map(BundleDateStock::date).distinct().count() != this.dates.size()) {
            throw new IllegalArgumentException("같은 날짜가 둘입니다: " + this.dates.stream().map(BundleDateStock::date).toList());
        }
        if (this.dates.stream().flatMap(date -> date.sessionStocks().stream()).map(QuantityZoneStock::zoneId).distinct().count() > 1) {
            throw new IllegalArgumentException("묶음권의 회차 재고는 한 구역이어야 합니다");
        }
    }

    /**
     * 묶는 날짜마다의 회차 재고로 판정한다.
     *
     * @throws IllegalArgumentException 날짜가 없거나, 같은 날짜가 둘이거나, 구역이 서로 다르면
     */
    public static BundleAvailability of(List<BundleDateStock> dates) {
        return new BundleAvailability(dates);
    }

    /** 묶는 날짜 — 받은 차례. */
    public List<BundleDateStock> dates() {
        return dates;
    }

    /** 팔 수 있는지 — 모든 날짜에 들어갈 회차가 있다. */
    public boolean isOnSale() {
        return dates.stream().allMatch(BundleDateStock::hasEnterableSession);
    }

    /** 들어갈 회차가 없는 날짜 — 판매가 멈춘 까닭. 팔 수 있으면 비어 있다. */
    public List<LocalDate> soldOutDates() {
        return dates.stream().filter(date -> !date.hasEnterableSession()).map(BundleDateStock::date).toList();
    }
}
