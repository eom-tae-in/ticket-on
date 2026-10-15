package com.ticketon.domain.admin;

/**
 * 관리자 조작 종류 — 조작 기록의 "무엇을"(docs/01 "관리자 2등급 — 모든 조작에 '누가 언제 무엇을'을 기록").
 * <p>
 * 조작마다 대상 종류가 하나로 정해져 있다({@link #targetKind()}). 기록이 조작과 다른 종류의 대상을 가리키지 않게 종류가 대상 종류를 정한다.
 * 조작은 그 기능을 만드는 이슈가 더한다(심사, 정지, 페이지 내리기 등). 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum AdminOperationKind {

    /** 관리자 계정 발급 — 최고 관리자만(docs/01 "관리자 2등급"). 대상은 발급한 관리자 계정이다. */
    ISSUE_ADMIN_ACCOUNT(AdminOperationTargetKind.ADMIN_ACCOUNT),

    /** 주최자 신청 승인 — 주최자가 활동이 되고 신청한 사람이 소유자가 된다(docs/01 "주최자 신청과 승인"). 대상은 승인한 신청서다. */
    APPROVE_ORGANIZER_APPLICATION(AdminOperationTargetKind.ORGANIZER_APPLICATION),

    /** 주최자 신청 반려 — 사유를 반드시 남긴다(docs/01 "반려"). 대상은 반려한 신청서다. */
    REJECT_ORGANIZER_APPLICATION(AdminOperationTargetKind.ORGANIZER_APPLICATION),

    /** 소유자 변경 사후 확인 — 처리 목록에서 빠진다(docs/12 "소유자가 바뀌면 관리자에게도 알립니다"). 대상은 확인한 소유자 변경 이력이다. */
    CONFIRM_OWNERSHIP_CHANGE(AdminOperationTargetKind.OWNERSHIP_CHANGE),

    /**
     * 소유자 이전 신청 승인 — 신청한 운영자가 소유자가 되고 신청 때의 소유자는 운영자로 남는다(docs/01 "소유자와 연락이 끊긴 경우",
     * docs/11 "대표가 바뀔 때"). 대상은 승인한 신청이다.
     */
    APPROVE_OWNERSHIP_CLAIM(AdminOperationTargetKind.OWNERSHIP_CLAIM),

    /** 소유자 이전 신청 반려 — 사유를 남긴다. 소유자는 그대로다. 대상은 반려한 신청이다. */
    REJECT_OWNERSHIP_CLAIM(AdminOperationTargetKind.OWNERSHIP_CLAIM),

    /**
     * 주최자명 변경 사후 확인 — 처리 목록에서 빠진다(docs/12 "관리자 대상 — 주최자명 변경 (사후 확인용)"). 이름은 되돌리지 않는다. 대상은 확인한
     * 주최자명 변경 이력이다.
     */
    CONFIRM_ORGANIZER_NAME_CHANGE(AdminOperationTargetKind.ORGANIZER_NAME_CHANGE),

    /**
     * 정산 계좌 · 사업자 정보 변경 승인 — 요청한 새 값이 주최자에 적용된다(docs/11 "정산 계좌 변경", docs/01 "판매자 정보"). 대상은 승인한 변경
     * 요청이다.
     */
    APPROVE_ORGANIZER_CHANGE_REQUEST(AdminOperationTargetKind.ORGANIZER_CHANGE_REQUEST),

    /** 정산 계좌 · 사업자 정보 변경 반려 — 사유를 남긴다. 기존 값이 그대로다. 대상은 반려한 변경 요청이다. */
    REJECT_ORGANIZER_CHANGE_REQUEST(AdminOperationTargetKind.ORGANIZER_CHANGE_REQUEST);

    private final AdminOperationTargetKind targetKind;

    AdminOperationKind(AdminOperationTargetKind targetKind) {
        this.targetKind = targetKind;
    }

    /** 이 조작의 대상 종류. */
    public AdminOperationTargetKind targetKind() {
        return targetKind;
    }
}
