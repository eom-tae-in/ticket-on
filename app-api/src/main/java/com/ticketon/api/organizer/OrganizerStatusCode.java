package com.ticketon.api.organizer;

import com.ticketon.domain.organizer.OrganizerStatus;

/**
 * 주최자 상태의 API 이름 — 화면 타입에 쓰인다. 주최자 전환 목록에서 활동 중인 주최자만 고르게 한다.
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 상태({@link OrganizerStatus})와 같다.
 */
public enum OrganizerStatusCode {

    /** 심사 중. */
    UNDER_REVIEW(OrganizerStatus.UNDER_REVIEW),

    /** 활동. */
    ACTIVE(OrganizerStatus.ACTIVE),

    /** 닫힘 — 화면에는 "활동 종료". */
    CLOSED(OrganizerStatus.CLOSED),

    /** 영구 정지. */
    PERMANENTLY_SUSPENDED(OrganizerStatus.PERMANENTLY_SUSPENDED);

    private final OrganizerStatus status;

    OrganizerStatusCode(OrganizerStatus status) {
        this.status = status;
    }

    static OrganizerStatusCode from(OrganizerStatus status) {
        for (OrganizerStatusCode code : values()) {
            if (code.status == status) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 주최자 상태: " + status);
    }
}
