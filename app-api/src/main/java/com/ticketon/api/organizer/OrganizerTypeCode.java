package com.ticketon.api.organizer;

import com.ticketon.domain.organizer.OrganizerType;

/**
 * 주최자 구분의 API 이름 — 화면 타입에 쓰인다. 선언 순서가 화면 순서다(docs/01 "신청 항목" — 주최자 구분 (개인/단체)).
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 구분({@link OrganizerType})과 같다.
 */
public enum OrganizerTypeCode {

    /** 개인. */
    INDIVIDUAL(OrganizerType.INDIVIDUAL),

    /** 단체. */
    GROUP(OrganizerType.GROUP);

    private final OrganizerType organizerType;

    OrganizerTypeCode(OrganizerType organizerType) {
        this.organizerType = organizerType;
    }

    /** 도메인 구분의 API 이름 — 관리자 심사 화면도 쓴다. */
    public static OrganizerTypeCode from(OrganizerType organizerType) {
        for (OrganizerTypeCode code : values()) {
            if (code.organizerType == organizerType) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 주최자 구분: " + organizerType);
    }

    OrganizerType toOrganizerType() {
        return organizerType;
    }
}
