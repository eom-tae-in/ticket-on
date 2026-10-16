package com.ticketon.domain.event;

/**
 * 지금 상태에서 그 항목을 고칠 수 있는지 — 상태별 수정 범위 표({@link EventEditScope})가 항목({@link EventEditTarget})마다 돌려주는 답.
 */
public enum EventEditAvailability {

    /** 고칠 수 있다. */
    ALLOWED,

    /**
     * 고칠 수 있되 항목 규칙을 지켜야 한다 — 티켓(예매 · 초대권, 취소 · 회수되지 않은 것)이 걸린 좌석 · 회차 · 구역은 옮기거나 지울 수 없고(#64),
     * 회차 시간은 그 회차 시작 1주일 전까지만 혼자 바꾸고({@link Event#requireSessionTimeEditable}) 티켓이 있으면 알림이 가며(#105), 정원은
     * 늘리기만 하고, 판매 대기의 장소는 초대권이 있으면 관리자 승인을 거친다(docs/04 "예매가 열린 뒤(판매 중) 수정 범위", docs/09 "오픈 전에
     * 발권하면 그 초대권이 걸린 것은 잠깁니다"). 규칙은 그 항목을 고치는 곳이 본다.
     */
    ALLOWED_WITH_RULES,

    /** 주최자가 혼자 고칠 수 없고 관리자 승인을 거친다 — 예매가 열린 뒤의 장소(docs/04 "장소 변경", #107). */
    NEEDS_ADMIN_APPROVAL,

    /** 고칠 수 없다. */
    LOCKED;

    /** 주최자가 지금 바로 고칠 수 있는지 — 항목 규칙이 붙어도 고칠 수는 있다. */
    public boolean isEditable() {
        return this == ALLOWED || this == ALLOWED_WITH_RULES;
    }
}
