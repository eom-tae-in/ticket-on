package com.ticketon.domain.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 묶음권이 묶는 날짜 하나의 재고 — 그 날짜 아래 회차들의, 묶음권 구역(수량형)의 재고. 주최자는 날짜만 고르고, 날짜 아래 회차가 여럿이면 어느
 * 회차로 들어갈지는 관객이 예매할 때 고른다(docs/05 "조합은 자동 생성하지 않습니다", docs/14 "묶음권 상품은 날짜를, 티켓은 회차를 가리킵니다").
 *
 * @param date          묶음권이 묶는 날짜
 * @param sessionStocks 그 날짜에 관객이 고를 수 있는 회차들의 재고 — 같은 수량형 구역. 예매할 수 없는 회차(취소됨 · 그 회차의 예매가 마감됨)는
 *                      부르는 쪽이 빼고 넣는다. 고를 회차가 없으면 비어 있다
 */
public record BundleDateStock(LocalDate date, List<QuantityZoneStock> sessionStocks) {

    /**
     * @throws IllegalArgumentException 같은 회차의 재고가 둘이거나, 구역이 서로 다르면
     */
    public BundleDateStock {
        Objects.requireNonNull(date, "date");
        sessionStocks = List.copyOf(Objects.requireNonNull(sessionStocks, "sessionStocks"));
        if (sessionStocks.stream().map(QuantityZoneStock::sessionId).distinct().count() != sessionStocks.size()) {
            throw new IllegalArgumentException("같은 회차의 재고가 둘입니다: " + date);
        }
        if (sessionStocks.stream().map(QuantityZoneStock::zoneId).distinct().count() > 1) {
            throw new IllegalArgumentException("묶음권의 회차 재고는 한 구역이어야 합니다: " + date);
        }
    }

    /** 이 날짜에 들어갈 회차가 있는지 — 잔여가 남은 회차가 하나라도 있다. */
    public boolean hasEnterableSession() {
        return sessionStocks.stream().anyMatch(stock -> stock.remaining() > 0);
    }
}
