package com.ticketon.application.admin.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 정산 계좌 · 사업자 정보 변경 확인 화면 조회 입력.
 *
 * @param admin           보는 관리자 — 로그인한 관리자(ADMIN) 주체
 * @param changeRequestId 변경 요청 id (1 이상)
 */
public record GetOrganizerChangeRequestReviewQuery(AccountRef admin, long changeRequestId) {

    public GetOrganizerChangeRequestReviewQuery {
        Objects.requireNonNull(admin, "admin");
        if (admin.kind() != AccountKind.ADMIN) {
            throw new IllegalArgumentException("변경 요청은 관리자가 확인합니다: " + admin.principalName());
        }
        if (changeRequestId < 1) {
            throw new IllegalArgumentException("변경 요청 id는 1 이상이어야 합니다: " + changeRequestId);
        }
    }
}
