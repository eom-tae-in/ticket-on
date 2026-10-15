package com.ticketon.domain.account;

import java.util.Objects;

/**
 * 플랫폼 관리자 등급 — 관리자·최고 관리자 두 등급(docs/01 "관리자 2등급").
 * <p>
 * 더 나누지 않는다(docs/99 B "관리자 등급 세분화"). 1만 명 이하 규모에서 "심사만 하는 인력"이 따로 있을 가능성이 낮아, 등급을 늘리면 쓸 사람
 * 없는 기능이 된다(docs/01). 최고 관리자는 관리자의 일을 전부 하고, 되돌릴 수 없는 일 둘(영구 정지, 관리자 계정 발급)을 더 한다
 * ({@link AdminAction}).
 * <p>
 * 이 이름은 저장소와 로그인 세션의 권한 이름에 그대로 남으므로 바꾸지 않는다.
 */
public enum AdminGrade {

    /** 관리자. 최고 관리자만 하는 일({@link AdminAction#isSuperAdminOnly})은 하지 못한다. */
    ADMIN("관리자"),

    /** 최고 관리자 — 관리자의 일 전부 + 영구 정지, 관리자 계정 발급. */
    SUPER_ADMIN("최고 관리자");

    private final String displayName;

    AdminGrade(String displayName) {
        this.displayName = displayName;
    }

    /** 이 등급으로 그 일을 할 수 있는지(docs/01 "관리자 2등급"). */
    public boolean can(AdminAction action) {
        Objects.requireNonNull(action, "action");
        return switch (this) {
            case SUPER_ADMIN -> true;
            case ADMIN -> !action.isSuperAdminOnly();
        };
    }

    /** 문서가 부르는 등급 이름(docs/01 "관리자 2등급" — 관리자 / 최고 관리자). 관리자 조작 기록의 요약에 쓴다. */
    public String displayName() {
        return displayName;
    }
}
