package com.ticketon.api.admin;

import com.ticketon.application.admin.query.AdminWorkKind;

/**
 * 관리자 처리 목록의 요청 종류의 API 이름 — 화면 타입에 쓰인다. 화면은 종류로 처리 화면을 고른다(주최자 신청 → 주최자 심사, 소유자 변경 →
 * 사후 확인, 소유자 이전 신청 → 소유자 이전 신청 심사, 주최자명 변경 → 사후 확인).
 * <p>
 * 안쪽 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 안쪽 종류({@link AdminWorkKind})와 같다. 종류는 그 기능의 이슈가 더한다.
 */
public enum AdminWorkKindCode {

    /** 주최자 신청 — 대상 id는 신청서 id다. */
    ORGANIZER_APPLICATION(AdminWorkKind.ORGANIZER_APPLICATION),

    /** 소유자 변경(사후 확인) — 대상 id는 소유자 변경 이력 id다. 확인하면 목록에서 빠진다. */
    OWNERSHIP_CHANGE(AdminWorkKind.OWNERSHIP_CHANGE),

    /** 소유자 이전 신청 — 대상 id는 신청 id다. 심사 화면에서 승인 · 반려하면 목록에서 빠진다. */
    OWNERSHIP_CLAIM(AdminWorkKind.OWNERSHIP_CLAIM),

    /** 주최자명 변경(사후 확인) — 대상 id는 주최자명 변경 이력 id다. 확인하면 목록에서 빠진다. */
    ORGANIZER_NAME_CHANGE(AdminWorkKind.ORGANIZER_NAME_CHANGE);

    private final AdminWorkKind kind;

    AdminWorkKindCode(AdminWorkKind kind) {
        this.kind = kind;
    }

    static AdminWorkKindCode from(AdminWorkKind kind) {
        for (AdminWorkKindCode code : values()) {
            if (code.kind == kind) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 처리 목록 종류: " + kind);
    }
}
