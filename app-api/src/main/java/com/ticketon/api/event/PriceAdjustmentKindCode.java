package com.ticketon.api.event;

import com.ticketon.domain.event.SessionPriceAdjustmentKind;

/**
 * 회차별 가격 조정 종류의 API 이름 — 화면 타입에 쓰인다. 조정 없음 · 금액 · 퍼센트(docs/05 "회차별 가격 조정 — 금액과 퍼센트 둘 다 허용").
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 종류({@link SessionPriceAdjustmentKind})와 같다.
 */
public enum PriceAdjustmentKindCode {

    /** 조정 없음 — 구역 기본가 그대로. */
    NONE(SessionPriceAdjustmentKind.NONE),

    /** 금액 조정 — 원 단위 ±("+10,000원"). */
    AMOUNT(SessionPriceAdjustmentKind.AMOUNT),

    /** 퍼센트 조정 — ±("-20%"). 조정한 가격은 100원 단위로 내린다(#48). */
    PERCENT(SessionPriceAdjustmentKind.PERCENT);

    private final SessionPriceAdjustmentKind kind;

    PriceAdjustmentKindCode(SessionPriceAdjustmentKind kind) {
        this.kind = kind;
    }

    static PriceAdjustmentKindCode from(SessionPriceAdjustmentKind kind) {
        for (PriceAdjustmentKindCode code : values()) {
            if (code.kind == kind) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 가격 조정 종류: " + kind);
    }
}
