package com.ticketon.domain.organizer;

/**
 * 정산 계좌 · 사업자 정보 변경 요청의 상태 — 소유자가 요청하고 관리자가 확인한 뒤 승인(적용)하거나 사유를 붙여 반려한다(docs/11 "정산 계좌
 * 변경", docs/01 "판매자 정보").
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum OrganizerChangeRequestStatus {

    /** 확인 중 — 관리자 처리 목록에 '정산 계좌 · 사업자 정보 변경 확인'으로 남아 있다. 기존 값이 그대로 쓰인다. */
    UNDER_REVIEW,

    /** 승인 — 요청한 값이 주최자에 적용됐다. */
    APPROVED,

    /** 반려 — 사유를 남기고 소유자에게 알렸다. 기존 값이 그대로다. */
    REJECTED
}
