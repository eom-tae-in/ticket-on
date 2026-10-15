package com.ticketon.api.organizer;

import com.ticketon.domain.organizer.StaffRole;

/**
 * 주최자 안에서의 역할의 API 이름 — 화면 타입에 쓰인다. 화면은 운영자에게 소유자 전용 메뉴를 감춘다(docs/01 "권한 두 가지").
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 역할({@link StaffRole})과 같다.
 */
public enum StaffRoleCode {

    /** 소유자 — 주최자에 1명. */
    OWNER(StaffRole.OWNER),

    /** 운영자. */
    OPERATOR(StaffRole.OPERATOR);

    private final StaffRole role;

    StaffRoleCode(StaffRole role) {
        this.role = role;
    }

    static StaffRoleCode from(StaffRole role) {
        for (StaffRoleCode code : values()) {
            if (code.role == role) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 역할: " + role);
    }
}
