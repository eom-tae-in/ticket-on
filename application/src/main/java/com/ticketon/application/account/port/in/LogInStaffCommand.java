package com.ticketon.application.account.port.in;

import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EnteredPassword;
import java.util.Objects;

/**
 * 운영진 로그인 입력.
 *
 * @param email    적은 이메일 — 운영진 가입 때 적은 로그인 아이디. 대소문자는 가리지 않는다.
 * @param password 적은 비밀번호 원문 — 저장된 해시와 비교만 하고 버린다
 */
public record LogInStaffCommand(EmailAddress email, EnteredPassword password) {

    public LogInStaffCommand {
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(password, "password");
    }
}
