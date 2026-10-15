package com.ticketon.application.admin.port.in;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.NewPassword;
import java.util.Objects;

/**
 * 관리자 계정 발급 입력.
 *
 * @param issuer   발급하는 관리자 — 로그인한 관리자(ADMIN) 주체. 최고 관리자여야 한다.
 * @param email    새 관리자의 이메일 — 로그인 아이디
 * @param password 새 관리자의 비밀번호 원문 — 해시로 바꾸는 데만 쓴다. 문자열로 바꾸면 가린다({@link NewPassword}).
 * @param grade    새 관리자의 등급
 */
public record IssueAdminAccountCommand(AccountRef issuer, EmailAddress email, NewPassword password, AdminGrade grade) {

    public IssueAdminAccountCommand {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(grade, "grade");
        if (issuer.kind() != AccountKind.ADMIN) {
            throw new IllegalArgumentException("관리자 계정은 관리자만 발급합니다: " + issuer.principalName());
        }
    }
}
