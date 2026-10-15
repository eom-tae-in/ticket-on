package com.ticketon.domain.account;

/**
 * 관객이 계정으로 하는 일 — 이용 정지 중에 막히는지가 정해져 있다(docs/01 "관객 이용 정지").
 * <p>
 * <b>막히는 것은 새 예매·변경·신고, 되는 것은 로그인·기존 티켓 보기·QR·취소</b>다. 산 표는 뺏지 않는다.
 * 전화번호 변경도 막는다 — 정지를 피하는 길이 되기 때문이다({@link #CHANGE_PHONE_NUMBER}).
 * 그래서 정지는 세션을 끊지 않는다. 각 기능은 시작할 때 {@link AudienceAccount#can}으로 확인한다
 * (이용 정지면 [예매하기]에서 막힘, docs/06 "전체 흐름").
 */
public enum AudienceAction {

    /** 로그인 — 정지 중에도 된다. */
    LOG_IN(false),

    /**
     * 내 계정 정보 보기(마이페이지 "계정 정보", docs/13) — 정지 중에도 된다. 정지로 막히는 것은 새 예매·변경·신고와 전화번호 변경뿐이고,
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
     * 로그인 수단 관리 — 카카오·구글 계정 연결·해제(docs/13 "계정 정보 · OAuth 연동") — 정지 중에도 된다. 정지로 막히는 것은 새 예매·변경·신고와 전화번호 변경뿐이다.
     */
    MANAGE_LOGIN_METHODS(false),

    /**
     * 전화번호 변경(마이페이지 "계정 정보 — 전화번호 변경", docs/13) — <b>정지 중에는 막힌다.</b>
     * <ul>
     *   <li>정지 중에 되는 것은 로그인·기존 티켓 보기·QR·취소로 적혀 있고, 번호 변경은 그 목록에 없다(docs/01 "관객 이용 정지 — 되는 것").</li>
     *   <li>정지된 계정이 탈퇴하면 그 번호를 차단 목록에 남겨 같은 번호로 새 계정을 만들어 피할 수 없게 한다(docs/01 "전화번호 — 이용 정지된
     *       계정의 탈퇴", "관객 이용 정지 — 탈퇴하면", "번호 하나에 계정 하나이므로 같은 번호로 새 계정을 만들어 피할 수 없습니다").
     *       정지 중에 번호를 바꿀 수 있으면 표를 취소하고 아무 번호로 바꾼 뒤(인증 없음) 탈퇴해 진짜 번호를 풀고 다시 가입할 수 있다.
     *       번호 변경은 칸을 고치는 것이라 이전 번호 이력도 남지 않는다.</li>
     * </ul>
     * 다른 계정이 쓰는 번호나 차단된 번호로 바꿀 수 없는 것은 정지와 상관없이 같다({@link AudienceAccount#changePhoneNumber}).
     */
    CHANGE_PHONE_NUMBER(true),

    /**
     * 마케팅 수신 동의·철회(마이페이지 "알림 설정 · 마케팅 수신", docs/13) — 정지 중에도 된다. 가입 때 받은 동의는 언제든 철회할 수
     * 있어야 하고(docs/01 "약관과 동의 — 가입 시", docs/12 "마케팅 수신은 구분해서 표시"), 정지로 막히는 것은 새 예매·변경·신고와 전화번호 변경뿐이다.
     */
    CHANGE_MARKETING_CONSENT(false),

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
