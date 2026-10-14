package com.ticketon.application.account.service;

import com.ticketon.application.account.port.out.EmailVerificationTokenRepository;
import com.ticketon.domain.account.EmailVerificationToken;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 메모리 이메일 인증 링크 저장소. */
final class FakeEmailVerificationTokenRepository implements EmailVerificationTokenRepository {

    private final List<EmailVerificationToken> tokens = new ArrayList<>();

    List<EmailVerificationToken> savedTokens() {
        return List.copyOf(tokens);
    }

    @Override
    public void add(EmailVerificationToken token) {
        tokens.add(token);
    }

    @Override
    public Optional<EmailVerificationToken> findByValue(String value) {
        return tokens.stream().filter(token -> token.value().equals(value)).findFirst();
    }
}
