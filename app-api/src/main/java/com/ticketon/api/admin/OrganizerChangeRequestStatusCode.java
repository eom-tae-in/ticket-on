package com.ticketon.api.admin;

import com.ticketon.domain.organizer.OrganizerChangeRequestStatus;

/**
 * 정산 계좌 · 사업자 정보 변경 요청 상태의 API 이름 — 화면 타입에 쓰인다. 확인 화면은 확인 중일 때만 [승인] [반려]를 보인다.
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 상태({@link OrganizerChangeRequestStatus})와 같다.
 */
public enum OrganizerChangeRequestStatusCode {

    /** 확인 중 — 기존 값이 그대로 쓰인다. */
    UNDER_REVIEW(OrganizerChangeRequestStatus.UNDER_REVIEW),

    /** 승인 — 요청한 값이 적용됐다. */
    APPROVED(OrganizerChangeRequestStatus.APPROVED),

    /** 반려 — 기존 값이 그대로다. */
    REJECTED(OrganizerChangeRequestStatus.REJECTED);

    private final OrganizerChangeRequestStatus status;

    OrganizerChangeRequestStatusCode(OrganizerChangeRequestStatus status) {
        this.status = status;
    }

    static OrganizerChangeRequestStatusCode from(OrganizerChangeRequestStatus status) {
        for (OrganizerChangeRequestStatusCode code : values()) {
            if (code.status == status) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 변경 요청 상태: " + status);
    }
}
