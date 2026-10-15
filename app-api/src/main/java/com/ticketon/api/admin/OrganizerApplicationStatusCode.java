package com.ticketon.api.admin;

import com.ticketon.domain.organizer.OrganizerApplicationStatus;

/**
 * 주최자 신청서 상태의 API 이름 — 화면 타입에 쓰인다. 심사 화면은 심사 중일 때만 [승인] [반려]를 보인다.
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 상태({@link OrganizerApplicationStatus})와 같다.
 */
public enum OrganizerApplicationStatusCode {

    /** 심사 중. */
    UNDER_REVIEW(OrganizerApplicationStatus.UNDER_REVIEW),

    /** 승인. */
    APPROVED(OrganizerApplicationStatus.APPROVED),

    /** 반려. */
    REJECTED(OrganizerApplicationStatus.REJECTED);

    private final OrganizerApplicationStatus status;

    OrganizerApplicationStatusCode(OrganizerApplicationStatus status) {
        this.status = status;
    }

    static OrganizerApplicationStatusCode from(OrganizerApplicationStatus status) {
        for (OrganizerApplicationStatusCode code : values()) {
            if (code.status == status) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 신청서 상태: " + status);
    }
}
