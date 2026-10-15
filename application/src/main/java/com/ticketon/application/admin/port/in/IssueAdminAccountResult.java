package com.ticketon.application.admin.port.in;

import com.ticketon.domain.account.AdminGrade;
import java.util.Objects;

/**
 * 관리자 계정 발급 결과.
 *
 * @param adminAccountId 발급한 관리자 계정 id
 * @param email          발급한 계정의 이메일 — 로그인 아이디
 * @param grade          발급한 계정의 등급
 */
public record IssueAdminAccountResult(long adminAccountId, String email, AdminGrade grade) {

    public IssueAdminAccountResult {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(grade, "grade");
    }
}
