package com.ticketon.domain.organizer;

/**
 * 소유자가 어떻게 바뀌었나 — 소유자 변경 이력({@link OwnershipChange}) 한 건이 어느 길로 생겼는지.
 * <p>
 * 길에 따라 관리자의 사후 확인 대상인지가 갈린다. 소유자 넘기기는 관리자를 거치지 않아 관리자 처리 목록에 '소유자 변경'(사후 확인)으로 쌓이고,
 * 소유자 이전 신청 승인은 관리자가 확인한 뒤 바꾼 것이라 쌓이지 않는다.
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum OwnershipChangeMethod {

    /**
     * 소유자 넘기기 — 소유자가 그 주최자의 운영자에게 넘겼다. 관리자를 거치지 않는다(docs/01 "소유자 넘기기"). 소유자가 바뀌면 관리자에게도
     * 알리므로(docs/12 "소유자가 바뀌면 관리자에게도 알립니다") 관리자가 사후 확인한다.
     */
    OWNER_HANDOVER,

    /**
     * 소유자 이전 신청 승인 — 소유자와 연락이 끊겨 운영자가 신청하고, 관리자가 확인한 뒤 바꿨다(docs/01 "소유자와 연락이 끊긴 경우",
     * docs/11 "대표가 바뀔 때"). 관리자가 직접 바꾼 것이라 다시 사후 확인하지 않는다. 신청({@link OwnershipClaim})을 가리킨다.
     */
    CLAIM_APPROVAL;

    /** 관리자의 사후 확인 대상인지 — 소유자 넘기기만이다. */
    public boolean needsAdminConfirmation() {
        return this == OWNER_HANDOVER;
    }
}
