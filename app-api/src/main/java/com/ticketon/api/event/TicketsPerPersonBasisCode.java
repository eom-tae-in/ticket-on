package com.ticketon.api.event;

import com.ticketon.domain.event.TicketsPerPersonBasis;

/**
 * 1인 총 매수를 세는 범위의 API 이름 — 화면 타입에 쓰인다. 날짜별 · 행사 전체(docs/06 "1인 총 매수 — 기준을 고릅니다"). 날짜가 하나뿐인 행사는
 * 둘이 같은 값이라 화면이 고르는 칸을 보여 주지 않는다.
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 범위({@link TicketsPerPersonBasis})와 같다.
 */
public enum TicketsPerPersonBasisCode {

    /** 날짜별. */
    PER_DATE(TicketsPerPersonBasis.PER_DATE),

    /** 행사 전체. */
    EVENT_TOTAL(TicketsPerPersonBasis.EVENT_TOTAL);

    private final TicketsPerPersonBasis basis;

    TicketsPerPersonBasisCode(TicketsPerPersonBasis basis) {
        this.basis = basis;
    }

    static TicketsPerPersonBasisCode from(TicketsPerPersonBasis basis) {
        for (TicketsPerPersonBasisCode code : values()) {
            if (code.basis == basis) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 1인 총 매수 범위: " + basis);
    }

    TicketsPerPersonBasis toBasis() {
        return basis;
    }
}
