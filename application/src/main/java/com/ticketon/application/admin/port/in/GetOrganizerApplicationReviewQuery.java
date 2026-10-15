package com.ticketon.application.admin.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.Objects;

/**
 * 주최자 심사 화면 조회 입력.
 *
 * @param reviewer      보는 관리자 — 로그인한 관리자(ADMIN) 주체
 * @param applicationId 신청서 id
 */
public record GetOrganizerApplicationReviewQuery(AccountRef reviewer, long applicationId) {

    public GetOrganizerApplicationReviewQuery {
        Objects.requireNonNull(reviewer, "reviewer");
        if (reviewer.kind() != AccountKind.ADMIN) {
            throw new IllegalArgumentException("주최자 신청은 관리자가 심사합니다: " + reviewer.principalName());
        }
        if (applicationId < 1) {
            throw new IllegalArgumentException("신청서 id는 1 이상이어야 합니다: " + applicationId);
        }
    }
}
