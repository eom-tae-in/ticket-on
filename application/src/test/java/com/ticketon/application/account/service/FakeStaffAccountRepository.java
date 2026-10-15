package com.ticketon.application.account.service;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.NicknameAvailability;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffAccountStatus;
import com.ticketon.domain.error.ConflictException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 메모리 운영진 계정 저장소 — 저장소 약속을 표처럼 지킨다: 탈퇴하지 않은 계정 사이에서 이메일(대소문자 무시)이 하나, 모든 계정 사이에서
 * 닉네임이 하나, 읽을 때마다 새 객체(저장하지 않은 변경은 남지 않음), 인증 저장은 인증 대기일 때만, 닉네임·비밀번호·탈퇴 저장은 활동
 * 중일 때만, 이메일로 찾을 때 탈퇴한 계정은 빼고. 저장소에 물은 횟수를 센다.
 */
final class FakeStaffAccountRepository implements StaffAccountRepository {

    private final Map<Long, StaffAccount> accounts = new LinkedHashMap<>();
    private AccountErrorCode signupRaceLoss;
    private boolean anotherNicknameChangeWinsNextSave;
    private int takenNicknameQueries;
    private WriteLog passwordSaveLog = new WriteLog();
    private long nextId = 1;

    /** 다음 저장 직전에 같은 이메일·닉네임의 다른 가입이 먼저 저장된 것처럼 — 확인은 통과했지만 저장에서 진다. */
    void letAnotherSignupWinNextAdd(AccountErrorCode lostOn) {
        signupRaceLoss = lostOn;
    }

    /** 다음 닉네임 저장 직전에 다른 운영진이 같은 닉네임을 먼저 저장한 것처럼. */
    void letAnotherNicknameChangeWinNextSave() {
        anotherNicknameChangeWinsNextSave = true;
    }

    List<StaffAccount> savedAccounts() {
        return accounts.values().stream().map(account -> copyOf(account, account.id().getAsLong())).toList();
    }

    /** 랜덤 닉네임 후보 가운데 쓰는 것을 물은 횟수. */
    int takenNicknameQueries() {
        return takenNicknameQueries;
    }

    @Override
    public EmailAvailability emailAvailability(EmailAddress email) {
        return registeredAccounts().anyMatch(account -> sameEmail(account.personalInformation().email(), email))
                ? EmailAvailability.ALREADY_REGISTERED : EmailAvailability.AVAILABLE;
    }

    @Override
    public NicknameAvailability nicknameAvailability(Nickname nickname) {
        return accounts.values().stream().anyMatch(account -> account.nickname().equals(nickname))
                ? NicknameAvailability.ALREADY_TAKEN : NicknameAvailability.AVAILABLE;
    }

    @Override
    public Set<Nickname> takenNicknamesAmong(Collection<Nickname> nicknames) {
        takenNicknameQueries++;
        Set<Nickname> held = accounts.values().stream().map(StaffAccount::nickname).collect(Collectors.toSet());
        return nicknames.stream().filter(held::contains).collect(Collectors.toSet());
    }

    @Override
    public StaffAccount add(StaffAccount newAccount) {
        if (signupRaceLoss != null) {
            AccountErrorCode lostOn = signupRaceLoss;
            signupRaceLoss = null;
            throw new ConflictException(lostOn, "동시 가입 경합(흉내)");
        }
        long id = nextId++;
        accounts.put(id, copyOf(newAccount, id));
        return copyOf(newAccount, id);
    }

    @Override
    public Optional<StaffAccount> findById(long id) {
        return Optional.ofNullable(accounts.get(id)).map(account -> copyOf(account, id));
    }

    @Override
    public Optional<StaffAccount> findRegisteredByEmail(EmailAddress email) {
        return registeredAccounts()
                .filter(account -> sameEmail(account.personalInformation().email(), email))
                .findFirst()
                .map(account -> copyOf(account, account.id().getAsLong()));
    }

    @Override
    public void saveEmailVerification(StaffAccount verifiedAccount) {
        long id = verifiedAccount.id().getAsLong();
        if (accounts.get(id).status() != StaffAccountStatus.PENDING_VERIFICATION) {
            throw new ConflictException(AccountErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        accounts.put(id, copyOf(verifiedAccount, id));
    }

    @Override
    public void saveNickname(StaffAccount changedAccount) {
        long id = changedAccount.id().getAsLong();
        boolean takenByOther = accounts.values().stream()
                .anyMatch(account -> account.id().getAsLong() != id && account.nickname().equals(changedAccount.nickname()));
        if (anotherNicknameChangeWinsNextSave || takenByOther) {
            anotherNicknameChangeWinsNextSave = false;
            throw new ConflictException(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN, "닉네임 변경 경합(흉내)");
        }
        if (accounts.get(id).status() != StaffAccountStatus.ACTIVE) {
            throw new ConflictException(AccountErrorCode.STAFF_NOT_ACTIVE);
        }
        accounts.put(id, copyOf(changedAccount, id));
    }

    /** 비밀번호 저장을 그 쓰기 기록에도 남긴다 — 링크 저장소와 같은 기록을 넘기면 잠금 순서(계정 → 링크)를 볼 수 있다. */
    void recordPasswordSavesIn(WriteLog writeLog) {
        passwordSaveLog = writeLog;
    }

    @Override
    public void savePassword(StaffAccount changedAccount) {
        long id = changedAccount.id().getAsLong();
        passwordSaveLog.record("비밀번호 저장 STAFF:" + id);
        if (accounts.get(id).status() != StaffAccountStatus.ACTIVE) {
            throw new ConflictException(AccountErrorCode.STAFF_NOT_ACTIVE);
        }
        accounts.put(id, copyOf(changedAccount, id));
    }

    @Override
    public void saveWithdrawal(StaffAccount withdrawnAccount) {
        long id = withdrawnAccount.id().getAsLong();
        if (accounts.get(id).status() != StaffAccountStatus.ACTIVE) {
            throw new ConflictException(AccountErrorCode.STAFF_WITHDRAWN, "탈퇴 경합(흉내)");
        }
        accounts.put(id, copyOf(withdrawnAccount, id));
    }

    private Stream<StaffAccount> registeredAccounts() {
        return accounts.values().stream().filter(account -> account.status() != StaffAccountStatus.WITHDRAWN);
    }

    private static boolean sameEmail(EmailAddress left, EmailAddress right) {
        return left.value().toLowerCase(Locale.ROOT).equals(right.value().toLowerCase(Locale.ROOT));
    }

    private static StaffAccount copyOf(StaffAccount account, long id) {
        if (account.isWithdrawn()) {
            return StaffAccount.restoreWithdrawn(id, account.nickname(), account.signedUpAt(),
                    account.withdrawnAt().orElseThrow());
        }
        return StaffAccount.restore(id, account.personalInformation(), account.nickname(), account.passwordHash(),
                account.signedUpAt(), account.status(), account.emailVerifiedAt().orElse(null));
    }
}
