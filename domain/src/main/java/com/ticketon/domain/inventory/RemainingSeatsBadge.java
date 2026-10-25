package com.ticketon.domain.inventory;

/**
 * 잔여 표시 — 남은 자리로 계산하는 표시(docs/13 "잔여 좌석 표시"). 상태가 아니라 계산되는 값이다 — 매진을 상태로 두면 취소표가 나올 때마다
 * 되돌려야 한다(docs/04 "'매진'은 상태가 아닙니다"). 목록 카드는 임박할 때만 배지를 단다 — "480석 남음"을 띄우면 모든 카드가 안 팔리는 공연처럼
 * 보인다.
 */
public enum RemainingSeatsBadge {

    /** 표시 없음 — 매진도 매진 임박도 아니다. */
    NONE,

    /** 매진 임박 — 잔여 10% 이하 또는 10석 이하(docs/99 C "잔여 배지"). */
    ALMOST_SOLD_OUT,

    /** 매진 — 잔여 0. */
    SOLD_OUT
}
