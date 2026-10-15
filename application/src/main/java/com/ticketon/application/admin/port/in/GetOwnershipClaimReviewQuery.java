package com.ticketon.application.admin.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 소유자 이전 신청 심사 화면 조회 입력.
 *
 * @param admin            보는 관리자 — 로그인한 관리자(ADMIN) 주체
 * @param ownershipClaimId 소유자 이전 신청 id (1 이상)
 */
public record GetOwnershipClaimReviewQuery(AccountRef admin, long ownershipClaimId) {

    public GetOwnershipClaimReviewQuery {
        Objects.requireNonNull(admin, "admin");
        if (admin.kind() != AccountKind.ADMIN) {
            throw new IllegalArgumentException("소유자 이전 신청은 관리자가 심사합니다: " + admin.principalName());
        }
        if (ownershipClaimId < 1) {
            throw new IllegalArgumentException("소유자 이전 신청 id는 1 이상이어야 합니다: " + ownershipClaimId);
        }
    }
}
