package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 주최 관리에서 일하는 운영진 — 누가(운영진 로그인) 어느 주최자로서 일하려는지. 주최 관리 기능의 Command · Query가 담는다.
 * <p>
 * 한 사람이 여러 주최자에 소속될 수 있어(docs/01 "운영자 초대") 주최 관리의 요청은 늘 어느 주최자의 일인지를 함께 갖는다(주최자 전환,
 * docs/13 "3. 주최 관리"). 들어오는 어댑터가 로그인 주체와 경로의 주최자 id로 만든다.
 * <p>
 * <b>이 값은 "그 주최자에서 일하겠다"는 요청일 뿐 권한이 아니다.</b> 서비스가 일을 하기 전에 {@link CheckStaffPermissionUseCase}로 지금
 * 소속 · 역할 · 주최자 상태를 확인한다.
 *
 * @param account     로그인한 주체 — 운영진(STAFF) 계정이어야 한다
 * @param organizerId 일하려는 주최자의 id (1 이상)
 */
public record ActingStaff(AccountRef account, long organizerId) {

    public ActingStaff {
        Objects.requireNonNull(account, "account");
        if (account.kind() != AccountKind.STAFF) {
            throw new IllegalArgumentException("주최 관리는 운영진 계정만 합니다: " + account.principalName());
        }
        if (organizerId < 1) {
            throw new IllegalArgumentException("주최자 id는 1 이상이어야 합니다: " + organizerId);
        }
    }

    /** 일하려는 사람의 운영진 계정 id. */
    public long staffAccountId() {
        return account.id();
    }
}
