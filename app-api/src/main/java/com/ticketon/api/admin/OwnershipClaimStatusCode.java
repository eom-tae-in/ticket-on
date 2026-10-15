package com.ticketon.api.admin;

import com.ticketon.domain.organizer.OwnershipClaimStatus;

/**
 * 소유자 이전 신청 상태의 API 이름 — 화면 타입에 쓰인다. 심사 화면은 심사 중일 때만 [승인] [반려]를 보인다.
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 상태({@link OwnershipClaimStatus})와 같다.
 */
public enum OwnershipClaimStatusCode {

    /** 심사 중. */
    UNDER_REVIEW(OwnershipClaimStatus.UNDER_REVIEW),

    /** 승인 — 신청한 운영자가 소유자가 됐다. */
    APPROVED(OwnershipClaimStatus.APPROVED),

    /** 반려 — 소유자는 그대로다. */
    REJECTED(OwnershipClaimStatus.REJECTED);

    private final OwnershipClaimStatus status;

    OwnershipClaimStatusCode(OwnershipClaimStatus status) {
        this.status = status;
    }

    static OwnershipClaimStatusCode from(OwnershipClaimStatus status) {
        for (OwnershipClaimStatusCode code : values()) {
            if (code.status == status) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 소유자 이전 신청 상태: " + status);
    }
}
