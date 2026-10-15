package com.ticketon.domain.organizer;

/**
 * 운영진이 주최자 안에서 하는 일 — 소유자만 하는지가 정해져 있다(docs/01 "권한 두 가지").
 * <p>
 * docs/01 "권한 두 가지" 표의 줄을 <b>한 항목씩 그대로</b> 나눠 옮겼다. 표의 한 줄에 여러 일이 묶여 있으면(예: "판매 중단·재개")
 * 일마다 하나씩 둔다. 줄의 O/X를 그대로 따른다 — 앞의 두 줄은 운영진 공통, 나머지 여섯 줄은 소유자만.
 * 표 밖에서 다른 문서가 누가 하는지 정해 둔 일은 맨 아래에 따로 모았다.
 * <p>
 * <b>권한은 소유자/운영자 두 가지로 고정</b>이고 주최자 단위다. 운영자는 그 주최자의 모든 행사를 다룬다. 행사별 권한, 주최자별 권한 설정은
 * 만들지 않는다(docs/99 B "운영자의 행사별 권한", "행사 제목 수정 권한 설정"). 누가 할 수 있는지는 {@link StaffRole#can}이 답한다.
 */
public enum StaffAction {

    // ── 표 1줄 (운영진 공통): 행사 만들기·수정, 초대권, 검표 계정, 현황·통계·정산 보기, 주최자 공개 정보(소개·SNS·로고) 편집

    /** 행사 만들기. */
    CREATE_EVENT(false),

    /** 행사 수정. */
    EDIT_EVENT(false),

    /** 초대권 — 발권·회수. */
    MANAGE_INVITATIONS(false),

    /** 검표 계정 — 발급·비밀번호 재발급. */
    MANAGE_CHECKIN_ACCOUNTS(false),

    /** 현황 보기 — 예매 현황·입장 현황. */
    VIEW_STATUS(false),

    /** 통계 보기. */
    VIEW_STATISTICS(false),

    /** 정산 보기. 정산 계좌를 다루는 일({@link #MANAGE_SETTLEMENT_ACCOUNT})과 다르다. */
    VIEW_SETTLEMENT(false),

    /** 주최자 공개 정보(소개·SNS·로고) 편집 — 운영진 누구나(docs/01 "공개 페이지 — 편집"). */
    EDIT_PUBLIC_PROFILE(false),

    // ── 표 2줄 (운영진 공통): 판매 중단·재개, 연기·장소 변경 신청

    /** 판매 중단. */
    PAUSE_SALES(false),

    /** 판매 재개. */
    RESUME_SALES(false),

    /** 연기 신청. */
    REQUEST_POSTPONEMENT(false),

    /** 장소 변경 신청. */
    REQUEST_VENUE_CHANGE(false),

    // ── 표 3줄 (소유자만, 배상이 걸림): 행사 취소 · 회차 취소 · 공연 중단 처리

    /** 행사 취소. */
    CANCEL_EVENT(true),

    /** 회차 취소. */
    CANCEL_SESSION(true),

    /** 공연 중단 처리. */
    HANDLE_PERFORMANCE_INTERRUPTION(true),

    // ── 표 4줄 (소유자만, 배상·환급이 걸림): 출연자 변경 알림 · 공연 지연 처리

    /** 출연자 변경 알림. */
    NOTIFY_PERFORMER_CHANGE(true),

    /** 공연 지연 처리. */
    HANDLE_PERFORMANCE_DELAY(true),

    // ── 표 5줄 (소유자만): 판매를 시작한 행사의 삭제 (팔린 표·초대권이 없었을 때만)

    /** 판매를 시작한 행사의 삭제. 팔린 표·초대권이 없었을 때만 지울 수 있다는 조건은 행사 쪽이 본다. */
    DELETE_EVENT_AFTER_SALES_STARTED(true),

    // ── 표 6줄 (소유자만): 정산 계좌

    /** 정산 계좌 — 변경 요청. 바꾸면 관리자 확인 뒤 적용된다(docs/11 "정산 계좌 변경"). */
    MANAGE_SETTLEMENT_ACCOUNT(true),

    // ── 표 7줄 (소유자만): 운영진 초대·내보내기

    /** 운영진 초대. */
    INVITE_STAFF(true),

    /** 운영진 내보내기. */
    REMOVE_STAFF(true),

    // ── 표 8줄 (소유자만): 소유자 넘기기 · 주최자 닫기

    /** 소유자 넘기기. */
    TRANSFER_OWNERSHIP(true),

    /** 주최자 닫기. */
    CLOSE_ORGANIZER(true),

    // ── 표 밖: 다른 문서가 누가 하는지 정해 둔 일

    /**
     * 판매를 시작하기 전 행사의 삭제 — 운영진 누구나. docs/11 "구조"의 "행사 삭제 (팔린 표·초대권이 없었을 때만. 판매 시작 후는 소유자만)"가
     * 판매 시작 전에는 소유자로 한정하지 않는다. 판매 시작 후는 {@link #DELETE_EVENT_AFTER_SALES_STARTED}.
     */
    DELETE_EVENT_BEFORE_SALES_STARTED(false),

    /**
     * 주최자명 변경 — 소유자만. 바로 적용되고 이력·알림이 남는다(docs/01 "공개 페이지 — 주최자명 변경",
     * docs/11 "구조"의 "주최자 정보 · 사업자 정보 · 정산 계좌 (소유자만)").
     */
    RENAME_ORGANIZER(true),

    /**
     * 사업자 정보 변경 요청 — 소유자만. 관리자 확인 뒤 적용된다(docs/01 "판매자 정보", docs/11 "구조"의
     * "주최자 정보 · 사업자 정보 · 정산 계좌 (소유자만)").
     */
    CHANGE_BUSINESS_INFORMATION(true),

    /**
     * 운영진 목록 보기 — 운영진 누구나. docs/13 "운영진 관리"의 "운영진 목록 (소유자 · 운영자)"에 소유자만 표시가 없다. 조작이 아니라 보기라
     * 조작 기록을 남기지 않는다.
     */
    VIEW_STAFF(false),

    /**
     * 운영자 초대 수락 — 수락하면 바로 운영자가 되므로 운영자로서 한 일로 남긴다(docs/01 "운영자 초대", "운영진의 모든 조작에 '누가 언제
     * 무엇을'을 기록합니다"). 거절은 그 주최자의 운영진이 되지 않은 사람의 일이라 운영진 조작이 아니다 — 초대에 답한 일시만 남는다.
     */
    ACCEPT_STAFF_INVITATION(false),

    /**
     * 이 주최자에서 나가기 — docs/13 "운영진 관리"의 "이 주최자에서 나가기 ※ 운영자". 소유자는 먼저 소유자를 넘기거나 주최자를 닫아야 하는데
     * (docs/01 "탈퇴와 주최자 닫기") 그것은 권한 표가 아니라 명단의 규칙이 막는다({@link OrganizerStaff#leave}) — 소유자는 모든 일을 할 수
     * 있으므로 권한으로는 거르지 않는다. 운영진 로그인을 탈퇴하며 소속 주최자마다 나갈 때도 이 일로 남긴다.
     */
    LEAVE_ORGANIZER(false),

    /**
     * 소유자 이전 신청 — docs/13 "운영진 관리"의 "소유자 이전 신청 ※ 운영자. 소유자와 연락이 끊긴 경우 관리자에게". 소유자는 신청하지 않고 직접
     * 넘기는데(docs/01 "소유자 넘기기") 그것은 권한 표가 아니라 신청의 규칙이 막는다({@link OwnershipClaim#submit}) — 소유자는 모든 일을 할
     * 수 있으므로 권한으로는 거르지 않는다. 승인 · 반려는 관리자의 일이라 관리자 조작 기록에 남는다.
     */
    CLAIM_OWNERSHIP(false);

    private final boolean ownerOnly;

    StaffAction(boolean ownerOnly) {
        this.ownerOnly = ownerOnly;
    }

    /** 소유자만 하는 일인지. 아니면 운영진(소유자·운영자) 누구나 한다. */
    public boolean isOwnerOnly() {
        return ownerOnly;
    }
}
