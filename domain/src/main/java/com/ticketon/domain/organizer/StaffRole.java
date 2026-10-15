package com.ticketon.domain.organizer;

import java.util.Objects;

/**
 * 주최자 안에서의 역할 — 소유자 1명, 운영자 N명(docs/01 "주최자와 운영진").
 * <p>
 * <b>권한은 이 두 가지뿐이고 주최자 단위다.</b> 한 사람이 여러 주최자에 소속되면 주최자마다 역할이 따로다.
 * 소유자는 배상과 정산 계좌가 걸린 일까지 전부 하고, 운영자는 그런 일을 뺀 운영을 한다({@link StaffAction}).
 * 소유자가 1명인 까닭은 배상과 정산 계좌의 책임질 사람이 분명해야 하기 때문이다(docs/01, docs/99 B "소유자 여러 명").
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum StaffRole {

    /** 소유자 — 주최자에 1명. 모든 일을 한다. */
    OWNER,

    /** 운영자 — 여러 명. 소유자만 하는 일({@link StaffAction#isOwnerOnly})은 하지 못한다. */
    OPERATOR;

    /** 이 역할로 그 일을 할 수 있는지(docs/01 "권한 두 가지"). */
    public boolean can(StaffAction action) {
        Objects.requireNonNull(action, "action");
        return switch (this) {
            case OWNER -> true;
            case OPERATOR -> !action.isOwnerOnly();
        };
    }
}
