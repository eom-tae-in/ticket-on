package com.ticketon.application.account.service;

import com.ticketon.application.account.port.out.StaffEmailVerificationTokenRepository;
import com.ticketon.domain.account.StaffEmailVerificationToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 메모리 운영진 로그인 이메일 인증 링크 저장소. */
final class FakeStaffEmailVerificationTokenRepository implements StaffEmailVerificationTokenRepository {

    private final List<StaffEmailVerificationToken> tokens = new ArrayList<>();

    List<StaffEmailVerificationToken> savedTokens() {
        return List.copyOf(tokens);
    }

    @Override
    public void add(StaffEmailVerificationToken token) {
        tokens.add(token);
    }

    @Override
    public Optional<StaffEmailVerificationToken> findByValue(String value) {
        return tokens.stream().filter(token -> token.value().equals(value)).findFirst();
    }
}
