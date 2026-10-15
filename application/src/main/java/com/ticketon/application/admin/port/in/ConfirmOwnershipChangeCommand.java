package com.ticketon.application.admin.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 소유자 변경 사후 확인 입력.
 *
 * @param admin             확인하는 관리자 — 로그인한 관리자(ADMIN) 주체
 * @param ownershipChangeId 확인할 소유자 변경 이력 id (1 이상)
 */
public record ConfirmOwnershipChangeCommand(AccountRef admin, long ownershipChangeId) {

    public ConfirmOwnershipChangeCommand {
        Objects.requireNonNull(admin, "admin");
        if (admin.kind() != AccountKind.ADMIN) {
            throw new IllegalArgumentException("소유자 변경은 관리자가 확인합니다: " + admin.principalName());
        }
        if (ownershipChangeId < 1) {
            throw new IllegalArgumentException("소유자 변경 id는 1 이상이어야 합니다: " + ownershipChangeId);
        }
    }
}
