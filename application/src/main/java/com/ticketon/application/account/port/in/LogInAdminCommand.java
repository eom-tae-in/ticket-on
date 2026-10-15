package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EnteredPassword;
import java.util.Objects;

/**
 * 관리자 로그인 입력.
 *
 * @param email    적은 이메일 — 관리자 계정을 발급받을 때 정한 로그인 아이디. 대소문자는 가리지 않는다.
 * @param password 적은 비밀번호 원문 — 저장된 해시와 비교만 하고 버린다
 */
public record LogInAdminCommand(EmailAddress email, EnteredPassword password) {

    public LogInAdminCommand {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(password, "password");
    }
}
