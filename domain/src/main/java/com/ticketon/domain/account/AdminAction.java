package com.ticketon.domain.account;

/**
 * 플랫폼 관리자가 하는 일 — 최고 관리자만 하는지가 정해져 있다(docs/01 "관리자 2등급").
 * <p>
 * docs/01 "관리자 2등급" 표의 칸을 <b>한 항목씩 그대로</b> 나눠 옮겼다. 한 항목에 여러 일이 묶여 있으면(예: "행사 페이지 내리기 · 다시 올리기")
 * 일마다 하나씩 둔다. '관리자' 줄의 일은 두 등급 모두 하고, '최고 관리자' 줄에 더해진 둘(영구 정지, 관리자 계정 발급)만 최고 관리자가 한다.
 * 표 밖에서 다른 문서가 관리자에게 맡긴 일은 맨 아래에 따로 모았다.
 * <p>
 * 기준은 <b>되돌릴 수 있는가</b>다. 영구 정지와 계정 발급은 아무나 하면 안 되지만, 나머지는 관리자를 뽑을 때 이미 신뢰한 사람이다(docs/01).
 * 심사와 운영을 더 나누지 않는다(docs/99 B "관리자 등급 세분화"). 누가 할 수 있는지는 {@link AdminGrade#can}이 답한다.
 */
public enum AdminAction {

    // ── '관리자' 줄 (두 등급 모두)

    /** 주최자 신청 심사 — 닫힌 주최자 다시 열기 신청 심사와 "다시 열기"로 돌리기를 포함한다. */
    REVIEW_ORGANIZER_APPLICATION(false),

    /** 주최자 신청 승인. */
    APPROVE_ORGANIZER_APPLICATION(false),

    /** 부적절한 운영진 닉네임 초기화. */
    RESET_STAFF_NICKNAME(false),

    /** 부적절한 운영진 소개 초기화. */
    RESET_STAFF_INTRODUCTION(false),

    /** 부적절한 주최자 로고 초기화. */
    RESET_ORGANIZER_LOGO(false),

    /** 주최자 정지 1~3단계. 4단계 영구 정지는 {@link #PERMANENTLY_SUSPEND_ORGANIZER}. */
    SUSPEND_ORGANIZER(false),

    /** 정산 완료 처리. */
    COMPLETE_SETTLEMENT(false),

    /** 미수금 납부 확인. */
    CONFIRM_RECEIVABLE_PAYMENT(false),

    /** 정산 계좌 변경 확인. */
    CONFIRM_SETTLEMENT_ACCOUNT_CHANGE(false),

    /** 사업자 정보 변경 확인. */
    CONFIRM_BUSINESS_INFORMATION_CHANGE(false),

    /** 비사업자 주최자 정보 제공 — 분쟁 시. */
    PROVIDE_NON_BUSINESS_ORGANIZER_INFORMATION(false),

    /** 소유자 이전 — 소유자와 연락이 끊긴 경우. */
    TRANSFER_ORGANIZER_OWNERSHIP(false),

    /** 행사 페이지 내리기. */
    TAKE_DOWN_EVENT_PAGE(false),

    /** 행사 페이지 다시 올리기. */
    RESTORE_EVENT_PAGE(false),

    /** QR 재발행. */
    REISSUE_TICKET_QR(false),

    /** 관객 이용 정지. */
    SUSPEND_AUDIENCE(false),

    /** 예매 대신 취소 — 오류 구제(docs/08). */
    CANCEL_BOOKING_ON_BEHALF(false),

    /** 불가항력 확인(docs/04). */
    CONFIRM_FORCE_MAJEURE(false),

    /** 공연 지연 대신 처리 — 관객 신고 시(docs/08). */
    HANDLE_PERFORMANCE_DELAY_ON_BEHALF(false),

    /** 출연자 변경 대신 처리 — 관객 신고 시(docs/08). */
    HANDLE_PERFORMER_CHANGE_ON_BEHALF(false),

    /** 마케팅 발송. */
    SEND_MARKETING_EMAIL(false),

    // ── '최고 관리자' 줄에 더해진 일 (최고 관리자만)

    /** 주최자 영구 정지(4단계). */
    PERMANENTLY_SUSPEND_ORGANIZER(true),

    /** 관리자 계정 발급. */
    ISSUE_ADMIN_ACCOUNT(true),

    // ── 표 밖: 다른 문서가 관리자에게 맡긴 일 (두 등급 모두 — 되돌릴 수 없는 일이 아니다)

    /**
     * 소유자 변경 사후 확인 — 소유자가 바뀌면 관리자에게도 알리고(docs/12 "소유자가 바뀌면 관리자에게도 알립니다"), 관리자는 알림 대신 처리
     * 목록으로 받는다(docs/12 "관리자 대상"). 관리자를 거치지 않고 이미 바뀐 일이라 승인이 아니라 확인이고, 확인하면 처리 목록에서 빠진다.
     */
    CONFIRM_OWNERSHIP_CHANGE(false),

    /**
     * 주최자명 변경 사후 확인 — 소유자가 주최자명을 바꾸면 관리자에게 알리고(docs/01 "공개 페이지 — 주최자명 변경"), 관리자는 알림 대신 처리
     * 목록으로 받는다(docs/12 "관리자 대상 — 주최자명 변경 (사후 확인용)", docs/13 "처리 목록 — 주최자명 변경 (사후 확인)"). 소유자가 바로
     * 바꾼 일이라 승인이 아니라 확인이고, 확인하면 처리 목록에서 빠진다. 사칭이면 신고·정지로 처리한다.
     */
    CONFIRM_ORGANIZER_NAME_CHANGE(false);

    private final boolean superAdminOnly;

    AdminAction(boolean superAdminOnly) {
        this.superAdminOnly = superAdminOnly;
    }

    /** 최고 관리자만 하는 일인지. 아니면 관리자·최고 관리자 누구나 한다. */
    public boolean isSuperAdminOnly() {
        return superAdminOnly;
    }
}
