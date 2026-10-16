package com.ticketon.api.organizer;

import com.ticketon.domain.organizer.OrganizerChangeRequestKind;

/**
 * 관리자 확인 뒤에 적용되는 변경의 종류의 API 이름 — 화면 타입에 쓰인다(docs/13 "처리 목록 — 정산 계좌 · 사업자 정보 변경 확인").
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 종류({@link OrganizerChangeRequestKind})와 같다.
 */
public enum OrganizerChangeKindCode {

    /** 정산 계좌 변경. */
    SETTLEMENT_ACCOUNT(OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT),

    /** 사업자 정보 변경 — 사업자 주최자만. */
    BUSINESS_INFORMATION(OrganizerChangeRequestKind.BUSINESS_INFORMATION);

    private final OrganizerChangeRequestKind kind;

    OrganizerChangeKindCode(OrganizerChangeRequestKind kind) {
        this.kind = kind;
    }

    /** 도메인 종류의 API 이름 — 관리자 확인 화면도 쓴다. */
    public static OrganizerChangeKindCode from(OrganizerChangeRequestKind kind) {
        for (OrganizerChangeKindCode code : values()) {
            if (code.kind == kind) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 변경 종류: " + kind);
    }
}
