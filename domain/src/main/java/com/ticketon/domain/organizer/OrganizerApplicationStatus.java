package com.ticketon.domain.organizer;

/**
 * 주최자 신청서 상태 — 플랫폼 관리자가 검토해 승인하거나 사유를 붙여 반려한다(docs/01 "주최자 신청과 승인").
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum OrganizerApplicationStatus {

    /** 심사 중 — 제출했고 아직 처리하지 않았다. 신청한 사람이 로그인하면 "심사 중" 화면만 본다. */
    UNDER_REVIEW,

    /** 승인 — 신청한 사람이 소유자가 되고 행사를 열 수 있다. */
    APPROVED,

    /** 반려 — 사유를 남기고 전달한다. 다시 신청하는 데 제한이 없다. */
    REJECTED
}
