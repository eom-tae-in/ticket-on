package com.ticketon.domain.event;

/**
 * 주최자의 [재개] 버튼 — 보이는지, 누를 수 있는지(docs/04 "판매 중단", docs/01 "관리자가 멈춘 판매는 주최자가 풀 수 없습니다", docs/13 "판매
 * 중단·재개 ※ 관리자가 멈췄으면 재개 불가 표시").
 */
public enum SalesResumeOption {

    /** 판매 중단 상태가 아니다 — 재개할 것이 없다. 판매 종료 이후에도 여기다 — 재개가 무의미해 버튼이 사라진다. */
    NOT_PAUSED,

    /** 관리자 조치로 중단됨 — 버튼에 "관리자 조치로 중단됨"을 보이고 누를 수 없다. 조치가 모두 풀린 뒤에야 누를 수 있다. */
    HELD_BY_ADMIN,

    /** 누를 수 있다. */
    AVAILABLE
}
