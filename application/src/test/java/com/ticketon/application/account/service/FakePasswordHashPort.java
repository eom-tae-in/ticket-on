package com.ticketon.application.account.service;

import com.ticketon.application.account.port.out.PasswordHashPort;
import com.ticketon.domain.account.EnteredPassword;
import com.ticketon.domain.account.NewPassword;
import com.ticketon.domain.account.PasswordHash;
import java.util.ArrayList;
import java.util.List;

/**
 * 테스트용 해시 — 원문이 그대로 남지 않는 값을 만든다. 진짜 해시 방식은 어댑터 테스트가 본다.
 * 비밀번호 확인({@link #matches})과 계정 없을 때의 비교({@link #simulateMatching})를 부른 차례대로 적어 둔다 — 로그인이
 * 어느 경우에나 해시 비교를 꼭 한 번 하는지(응답 시간으로 가입 여부가 드러나지 않게) 보려고.
 */
final class FakePasswordHashPort implements PasswordHashPort {

    /** 해시 비교를 부른 것 하나. */
    enum Comparison {
        /** 저장된 해시와 비교했다. */
        MATCHES,
        /** 비교할 해시가 없어 같은 만큼 시간을 들였다. */
        SIMULATED_MATCHING
    }

    private final List<Comparison> comparisons = new ArrayList<>();

    static PasswordHash hashOf(String rawPassword) {
        return new PasswordHash("{fake}" + new StringBuilder(rawPassword).reverse());
    }

    /** 부른 차례대로의 해시 비교. */
    List<Comparison> comparisons() {
        return List.copyOf(comparisons);
    }

    @Override
    public PasswordHash hash(NewPassword password) {
        return hashOf(password.value());
    }

    @Override
    public boolean matches(EnteredPassword password, PasswordHash passwordHash) {
        comparisons.add(Comparison.MATCHES);
        return hashOf(password.value()).equals(passwordHash);
    }

    @Override
    public void simulateMatching(EnteredPassword password) {
        comparisons.add(Comparison.SIMULATED_MATCHING);
    }
}
