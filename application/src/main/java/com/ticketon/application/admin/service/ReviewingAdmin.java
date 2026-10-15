package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminAccount;

/**
 * 심사하는 관리자 — 로그인한 관리자 주체로 저장소에서 계정을 읽는다. 등급은 세션이 아니라 저장소의 지금 등급으로 본다
 * (관리자 계정 발급과 같다 — 규칙의 마지막 판정은 도메인 {@link AdminAccount#requirePermission}이 한다).
 */
final class ReviewingAdmin {

    private ReviewingAdmin() {
    }

    /**
     * @throws IllegalStateException 로그인한 관리자 계정이 저장소에 없으면 — 관리자 계정은 지우지 않으므로 생기지 않는 일이다
     */
    static AdminAccount of(AdminAccountRepository adminAccountRepository, AccountRef reviewer) {
        return adminAccountRepository.findById(reviewer.id())
                .orElseThrow(() -> new IllegalStateException(
                        "로그인한 관리자 계정을 찾지 못했습니다: " + reviewer.principalName()));
    }
}
