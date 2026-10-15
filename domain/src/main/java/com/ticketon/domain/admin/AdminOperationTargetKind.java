package com.ticketon.domain.admin;

/**
 * 관리자 조작의 대상 종류 — 조작 기록이 가리키는 것이 무엇인지(대상 id는 종류마다 따로 매긴다).
 * <p>
 * 종류는 그 조작을 만드는 이슈가 더한다(주최자, 행사, 관객 계정, 예매 등). 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum AdminOperationTargetKind {

    /** 관리자 계정. */
    ADMIN_ACCOUNT,

    /** 주최자 신청서 — 심사받는 주최자는 신청서가 가리킨다. */
    ORGANIZER_APPLICATION,

    /** 소유자 변경 이력 — 소유자를 넘긴 한 건. */
    OWNERSHIP_CHANGE
}
