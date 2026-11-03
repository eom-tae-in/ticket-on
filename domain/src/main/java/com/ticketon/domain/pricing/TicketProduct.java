package com.ticketon.domain.pricing;

/**
 * 티켓을 산 상품 — 회차 하나짜리 티켓(페스티벌의 "1일권") 또는 묶음권 상품 하나(docs/05 "적용 범위 — 상품별로도 끌 수 있습니다 — 1일권, 각
 * 묶음권").
 * <p>
 * 조기예매의 상품별 켜고 끔({@link EarlyBirdScope})이 이 값으로 상품을 가리킨다. 묶음권은 상품의 id만 갖는다 — 묶음권 상품의 규칙(포함 날짜 ·
 * 대상 구역 · 할인율)은 묶음권 상품(#50)이 갖고, 여기서는 "어느 상품인가"만 가린다.
 */
public sealed interface TicketProduct {

    /** 회차 하나짜리 티켓 — 회차형 공연의 일반 티켓, 페스티벌의 1일권. */
    static TicketProduct singleSession() {
        return new SingleSession();
    }

    /**
     * 묶음권 상품 하나.
     *
     * @param bundleProductId 묶음권 상품 id, 1 이상
     */
    static TicketProduct bundle(long bundleProductId) {
        return new Bundle(bundleProductId);
    }

    /** 회차 하나짜리 티켓. 값이 없어 모두 같다. */
    record SingleSession() implements TicketProduct {
    }

    /**
     * 묶음권 상품 하나. 3일권 · 2일권처럼 상품마다 따로 켜고 끈다.
     *
     * @param bundleProductId 묶음권 상품 id, 1 이상
     */
    record Bundle(long bundleProductId) implements TicketProduct {

        public Bundle {
            if (bundleProductId < 1) {
                throw new IllegalArgumentException("묶음권 상품 id는 1 이상이어야 합니다: " + bundleProductId);
            }
        }
    }
}
