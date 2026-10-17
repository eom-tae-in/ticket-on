package com.ticketon.domain.event;

/**
 * 회차별 가격 조정의 종류 — 조정 없음 / 금액 ± / 퍼센트 ±(docs/05 "회차별 가격 조정 — 금액과 퍼센트 둘 다 허용"). 저장 · 조회에서 조정 값의 꼴을
 * 가른다({@link SessionPriceAdjustment#kind}).
 * <p>
 * 이 상수 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum SessionPriceAdjustmentKind {

    /** 조정 없음 — 구역 기본가 그대로. */
    NONE,

    /** 금액 조정 — 원 단위로 더하거나 뺀다("3/15(토) +10,000원"). */
    AMOUNT,

    /** 퍼센트 조정 — 퍼센트로 더하거나 뺀다("3/12(수, 프리뷰) -20%"). */
    PERCENT
}
