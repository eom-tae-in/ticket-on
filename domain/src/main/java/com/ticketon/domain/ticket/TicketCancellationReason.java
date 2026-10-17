package com.ticketon.domain.ticket;

import java.util.Objects;

/**
 * 취소 사유 8종(docs/07 "티켓은 '취소됐나'만 기록합니다", docs/14). 티켓은 "유효 / 취소됨" 칸 대신 <b>취소 시각과 사유</b>를 남긴다.
 * <p>
 * <b>사유를 티켓에 직접 적는 이유</b> — 환불 기록에서 사유를 읽을 수도 있지만 초대권 회수는 돈이 오가지 않아 환불 기록이 없다. 모든 취소에 사유가
 * 붙으려면 티켓에 있어야 한다. 사유는 예매 카드에 "취소됨 · 관객 취소"처럼 그대로 보인다({@link #label}).
 * <p>
 * 사유마다 붙을 수 있는 부모가 정해져 있다({@link #appliesTo}). 관객 취소 · 좌석 변경 · 관리자 조치 · 결제 오류 환불은 돈이 오간 예매 티켓에만,
 * 초대권 회수는 초대권 티켓에만, 회차 취소 · 행사 취소 · 공연 중단은 둘 다에 붙는다 — 초대권도 같은 사유로 취소된다(docs/09 "주최자가 회차를
 * 취소하면").
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum TicketCancellationReason {

    /** 관객 취소 — 관객이 직접(docs/08 "관객이 직접, 자동 환불"). */
    AUDIENCE_CANCELLATION("관객 취소", true, false),

    /** 좌석 변경 — 변경으로 기존 티켓이 바뀔 때. 변경 = 기존 티켓 취소 + 새 티켓 발급이다(docs/08 "예매 변경"). 다른 회차로 바꿔도 이 사유다. */
    SEAT_CHANGE("좌석 변경", true, false),

    /** 회차 취소 — 주최자가 회차를 취소(docs/04 "회차 취소"). 묶음권은 하루가 취소돼도 티켓을 그대로 두고 그날 몫만 돌려준다(docs/08). */
    SESSION_CANCELLED("회차 취소", true, true),

    /**
     * 행사 취소 — 주최자의 행사 취소, 관리자 정지 3단계(docs/04 "행사 취소", docs/01). <b>정지 3단계도 이 사유다</b> — 관객 입장에서는 같은 일이고,
     * 자발적 취소인지 강제 취소인지는 주최자 이력에 따로 남는다.
     */
    EVENT_CANCELLED("행사 취소", true, true),

    /** 공연 중단 — 예정 시간의 절반 이하만 하고 멈춤(docs/08 "공연 중단"). */
    PERFORMANCE_STOPPED("공연 중단", true, true),

    /** 관리자 조치 — 오류로 기한 안에 못 한 취소를 관리자가 대신(docs/08 "관리자 대신 취소"). */
    ADMIN_ACTION("관리자 조치", true, false),

    /** 초대권 회수 — 주최자가 초대권을 회수(docs/09 "발권 후"). 돈이 오가지 않아 환불 기록이 없다. */
    INVITATION_RECALLED("초대권 회수", false, true),

    /** 결제 오류 환불 — 승인은 났는데 좌석 확정이 안 된 경우(docs/06 "실패·중단·이탈을 구분합니다"). */
    PAYMENT_ERROR_REFUND("결제 오류 환불", true, false);

    private final String label;
    private final boolean appliesToBooking;
    private final boolean appliesToInvitation;

    TicketCancellationReason(String label, boolean appliesToBooking, boolean appliesToInvitation) {
        this.label = label;
        this.appliesToBooking = appliesToBooking;
        this.appliesToInvitation = appliesToInvitation;
    }

    /** 화면에 보이는 사유 — "관객 취소", "회차 취소"(docs/07 취소 사유 표). */
    public String label() {
        return label;
    }

    /** 그 부모의 티켓에 붙을 수 있는 사유인지. */
    public boolean appliesTo(TicketParentKind parentKind) {
        Objects.requireNonNull(parentKind, "parentKind");
        return switch (parentKind) {
            case BOOKING -> appliesToBooking;
            case INVITATION -> appliesToInvitation;
        };
    }
}
