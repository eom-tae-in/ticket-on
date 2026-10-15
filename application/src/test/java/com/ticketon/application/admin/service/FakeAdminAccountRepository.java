package com.ticketon.application.admin.service;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.error.ConflictException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 메모리 관리자 계정 저장소 — 저장소 약속을 표처럼 지킨다: 관리자 계정 사이에서 이메일(대소문자 무시)이 하나이고, 확인 뒤 같은 이메일이
 * 먼저 저장됐으면 저장에서 충돌로 막는다.
 */
final class FakeAdminAccountRepository implements AdminAccountRepository {

    private final Map<Long, AdminAccount> accounts = new LinkedHashMap<>();
    private boolean anotherIssueWinsNextAdd;
    private long nextId = 1;

    /** 발급 없이 바로 넣는다 — 맨 처음 최고 관리자를 만드는 방법은 #22가 정하므로 테스트는 픽스처로 넣는다. */
    long store(String email, PasswordHash passwordHash, AdminGrade grade, Instant issuedAt) {
        long id = nextId++;
        accounts.put(id, AdminAccount.restore(id, EmailAddress.of(email), passwordHash, grade, issuedAt));
        return id;
    }

    /** 다음 저장 직전에 같은 이메일의 다른 발급이 먼저 저장된 것처럼 — 확인은 통과했지만 저장에서 진다. */
    void letAnotherIssueWinNextAdd() {
        anotherIssueWinsNextAdd = true;
    }

    List<AdminAccount> savedAccounts() {
        return List.copyOf(accounts.values());
    }

    @Override
    public EmailAvailability emailAvailability(EmailAddress email) {
        return findByEmail(email).isPresent() ? EmailAvailability.ALREADY_REGISTERED : EmailAvailability.AVAILABLE;
    }

    @Override
    public AdminAccount add(AdminAccount newAccount) {
        if (newAccount.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 관리자 계정입니다");
        }
        if (anotherIssueWinsNextAdd || findByEmail(newAccount.email()).isPresent()) {
            anotherIssueWinsNextAdd = false;
            throw new ConflictException(AccountErrorCode.ADMIN_EMAIL_ALREADY_REGISTERED, "동시 발급 경합");
        }
        long id = store(newAccount.email().value(), newAccount.passwordHash(), newAccount.grade(), newAccount.issuedAt());
        return accounts.get(id);
    }

    @Override
    public Optional<AdminAccount> findById(long id) {
        return Optional.ofNullable(accounts.get(id));
    }

    @Override
    public Optional<AdminAccount> findByEmail(EmailAddress email) {
        return accounts.values().stream()
                .filter(account -> lowercase(account.email()).equals(lowercase(email)))
                .findFirst();
    }

    private static String lowercase(EmailAddress email) {
        return email.value().toLowerCase(Locale.ROOT);
    }
}
