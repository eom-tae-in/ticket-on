package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import java.util.Objects;

/**
 * 관리자 로그인 결과.
 *
 * @param account 로그인할 주체 — 관리자(ADMIN) 계정. 들어오는 어댑터가 이 주체로 세션을 연다.
 * @param grade   그 관리자의 등급 — 들어오는 어댑터가 세션에 함께 담아 최고 관리자 전용 경로를 가른다
 */
public record LogInAdminResult(AccountRef account, AdminGrade grade) {

    public LogInAdminResult {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(grade, "grade");
        if (account.kind() != AccountKind.ADMIN) {
            throw new IllegalArgumentException("관리자 로그인 결과는 관리자 계정이어야 합니다: " + account.principalName());
        }
    }
}
