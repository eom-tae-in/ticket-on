package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.domain.account.EnteredPassword;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.account.PasswordHash;

/** 테스트용 해시 — 원문이 그대로 남지 않는 값을 만든다. 진짜 해시 방식은 어댑터 테스트가 본다. */
final class FakePasswordHashPort implements PasswordHashPort {

    static PasswordHash hashOf(String rawPassword) {
        return new PasswordHash("{fake}" + new StringBuilder(rawPassword).reverse());
    }

    @Override
    public PasswordHash hash(NewPassword password) {
        return hashOf(password.value());
    }

    @Override
    public boolean matches(EnteredPassword password, PasswordHash passwordHash) {
        return hashOf(password.value()).equals(passwordHash);
    }

    @Override
    public void simulateMatching(EnteredPassword password) {
    }
}
