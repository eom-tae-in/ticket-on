package com.ticketon.domain.account;

/**
 * 관객이 계정으로 하는 일 — 이용 정지 중에 막히는지가 정해져 있다(docs/01 "관객 이용 정지").
 * <p>
 * <b>막히는 것은 새 예매·변경·신고, 되는 것은 로그인·기존 티켓 보기·QR·취소</b>다. 산 표는 뺏지 않는다.
 * 그래서 정지는 세션을 끊지 않는다. 각 기능은 시작할 때 {@link AudienceAccount#can}으로 확인한다
 * (이용 정지면 [예매하기]에서 막힘, docs/06 "전체 흐름").
 */
public enum AudienceAction {

    /** 로그인 — 정지 중에도 된다. */
    LOG_IN(false),

    /**
     * 내 계정 정보 보기(마이페이지 "계정 정보", docs/13) — 정지 중에도 된다. 정지로 막히는 것은 새 예매·변경·신고뿐이고,
     * 화면은 이 정보로 정지 여부를 알려 준다.
     */
    VIEW_ACCOUNT(false),

    /** 기존 티켓 보기 — 정지 중에도 된다. */
    VIEW_TICKETS(false),

    /** 티켓 QR 보기 — 정지 중에도 된다. 산 표로 입장할 수 있어야 한다. */
    SHOW_TICKET_QR(false),

    /** 티켓 취소 — 정지 중에도 된다. */
    CANCEL_TICKET(false),

    /**
     * 비밀번호 찾기·재설정 — 정지 중에도 된다. 정지 중에도 로그인은 되므로 로그인을 되찾는 일도 막지 않는다
     * (docs/01 "같은 이메일로 양쪽 가입"의 비밀번호 찾기).
     */
    RESET_PASSWORD(false),

    /**
     * 로그인 수단 관리 — 카카오·구글 계정 연결·해제(docs/13 "계정 정보 · OAuth 연동") — 정지 중에도 된다. 정지로 막히는 것은 새 예매·변경·신고뿐이다.
     */
    MANAGE_LOGIN_METHODS(false),

    /** 새 예매 — 정지 중에는 막힌다. */
    BOOK(true),

    /** 예매 변경(좌석·회차) — 정지 중에는 막힌다. */
    CHANGE_BOOKING(true),

    /** 신고 — 정지 중에는 막힌다. 반복되는 허위 신고가 정지 사유이기도 하다. */
    REPORT(true);

    private final boolean restrictedWhileSuspended;

    AudienceAction(boolean restrictedWhileSuspended) {
        this.restrictedWhileSuspended = restrictedWhileSuspended;
    }

    /** 이용 정지 중에 막히는 일인지. */
    public boolean isRestrictedWhileSuspended() {
        return restrictedWhileSuspended;
    }
}
