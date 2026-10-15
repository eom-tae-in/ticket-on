package com.ticketon.application.account.service;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.NicknameAvailability;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffPersonalInformation;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 메모리 운영진 계정 저장소에 상태별 계정을 넣는다. 이름은 박정훈, 비밀번호는 {@value #RAW_PASSWORD}다.
 */
final class StoredStaffAccounts {

    static final String RAW_PASSWORD = "junghoon1234";

    static final Instant SIGNUP_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private final FakeStaffAccountRepository repository;

    StoredStaffAccounts(FakeStaffAccountRepository repository) {
        this.repository = repository;
    }

    /** 가입만 하고 이메일 인증을 마치지 않은 계정. */
    long pending(String email, String nickname) {
        StaffAccount newAccount = StaffAccount.signUp(StaffPersonalInformation.of("박정훈", email), Nickname.of(nickname),
                FakePasswordHashPort.hashOf(RAW_PASSWORD), EmailAvailability.AVAILABLE, NicknameAvailability.AVAILABLE,
                SIGNUP_TIME);
        return repository.add(newAccount).id().getAsLong();
    }

    /** 이메일 인증까지 마친 계정. */
    long active(String email, String nickname) {
        long id = pending(email, nickname);
        StaffAccount account = repository.findById(id).orElseThrow();
        account.verifyEmail(SIGNUP_TIME.plus(Duration.ofMinutes(5)));
        repository.saveEmailVerification(account);
        return id;
    }

    /** 활동 중이던 계정이 탈퇴한 것 — 이름 · 이메일 · 비밀번호 해시는 파기되고 닉네임만 남았다. */
    long withdrawn(String email, String nickname) {
        long id = active(email, nickname);
        StaffAccount account = repository.findById(id).orElseThrow();
        account.withdraw(SIGNUP_TIME.plus(Duration.ofDays(30)));
        repository.saveWithdrawal(account);
        return id;
    }
}
