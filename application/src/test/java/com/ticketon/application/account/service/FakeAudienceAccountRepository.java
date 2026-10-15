package com.ticketon.application.account.service;

import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAccountStatus;
import com.ticketon.domain.account.ConsentRecord;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.PhoneNumber;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.error.ConflictException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 메모리 관객 계정 저장소 — 저장소 약속을 표처럼 지킨다: 탈퇴하지 않은 계정 사이에서 번호·이메일(대소문자 무시)이 하나,
 * 읽을 때마다 새 객체(저장하지 않은 변경은 남지 않음), 인증 저장은 인증 대기일 때만, 비밀번호 저장은 활동·이용 정지일 때만, 전화번호 저장은
 * 활동 중일 때만,
 * 이메일로 찾을 때 탈퇴한 계정은 빼고. 동의 기록은 더하기만 한다.
 * <p>
 * 잠그고 읽기({@link #findByIdForUpdate})는 메모리라 기다릴 일이 없어 그냥 읽고, 쓰기 기록에 잠금을 남긴다. 진짜 DB에서 두 요청이 차례를
 * 기다리는 모습은 저장 어댑터 테스트가 본다.
 */
final class FakeAudienceAccountRepository implements AudienceAccountRepository {

    private final Map<Long, AudienceAccount> accounts = new LinkedHashMap<>();
    private final Set<String> blockedPhoneNumbers = new HashSet<>();
    private boolean anotherSignupWinsNextAdd;
    private boolean anotherAccountTakesNextPhoneNumber;
    private WriteLog passwordSaveLog = new WriteLog();
    private final WriteLog accountWriteLog = new WriteLog();
    private long nextId = 1;

    /** 이용 정지 후 탈퇴로 차단 목록에 남은 번호를 흉내 낸다(#83이 채운다). */
    void block(PhoneNumber phoneNumber) {
        blockedPhoneNumbers.add(phoneNumber.digits());
    }

    /** 다음 저장 직전에 같은 번호의 다른 가입이 먼저 저장된 것처럼 — 확인은 통과했지만 저장에서 진다. */
    void letAnotherSignupWinNextAdd() {
        anotherSignupWinsNextAdd = true;
    }

    /** 다음 전화번호 저장 직전에 다른 계정이 같은 번호를 먼저 저장한 것처럼 — 확인은 통과했지만 저장에서 진다. */
    void letAnotherAccountTakeNextPhoneNumber() {
        anotherAccountTakesNextPhoneNumber = true;
    }

    /** 잠그고 읽기·전화번호 저장·동의 기록 추가를 부른 차례대로. */
    List<String> accountWrites() {
        return accountWriteLog.writes();
    }

    /**
     * 저장된 계정을 바꿔 둔다 — 아직 저장 기능이 없는 관리자 이용 정지(#125)·회원 탈퇴(#83)가 만들 상태를 흉내 낸다.
     *
     * @param changedAccount 저장된 계정을 읽어 상태를 바꾼 것(id가 있어야 한다)
     */
    void replace(AudienceAccount changedAccount) {
        long id = changedAccount.id().orElseThrow();
        if (!accounts.containsKey(id)) {
            throw new IllegalArgumentException("저장된 적 없는 계정: " + id);
        }
        accounts.put(id, copyOf(changedAccount, id));
    }

    List<AudienceAccount> savedAccounts() {
        return accounts.values().stream().map(account -> copyOf(account, account.id().getAsLong())).toList();
    }

    @Override
    public PhoneNumberAvailability phoneNumberAvailability(PhoneNumber phoneNumber) {
        if (registeredAccounts().anyMatch(account -> account.personalInformation().phoneNumber().equals(phoneNumber))) {
            return PhoneNumberAvailability.ALREADY_REGISTERED;
        }
        return blockedPhoneNumbers.contains(phoneNumber.digits()) ? PhoneNumberAvailability.BLOCKED
                : PhoneNumberAvailability.AVAILABLE;
    }

    @Override
    public EmailAvailability emailAvailability(EmailAddress email) {
        return registeredAccounts().anyMatch(account -> sameEmail(account.personalInformation().email(), email))
                ? EmailAvailability.ALREADY_REGISTERED : EmailAvailability.AVAILABLE;
    }

    @Override
    public AudienceAccount add(AudienceAccount newAccount) {
        if (anotherSignupWinsNextAdd) {
            anotherSignupWinsNextAdd = false;
            throw new ConflictException(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED, "동시 가입 경합(흉내)");
        }
        long id = nextId++;
        accounts.put(id, copyOf(newAccount, id));
        return copyOf(newAccount, id);
    }

    @Override
    public Optional<AudienceAccount> findById(long id) {
        return Optional.ofNullable(accounts.get(id)).map(account -> copyOf(account, id));
    }

    @Override
    public Optional<AudienceAccount> findByIdForUpdate(long id) {
        accountWriteLog.record("잠금 AUDIENCE:" + id);
        return findById(id);
    }

    @Override
    public Optional<AudienceAccount> findRegisteredByEmail(EmailAddress email) {
        return registeredAccounts()
                .filter(account -> sameEmail(account.personalInformation().email(), email))
                .findFirst()
                .map(account -> copyOf(account, account.id().getAsLong()));
    }

    @Override
    public void saveEmailVerification(AudienceAccount verifiedAccount) {
        long id = verifiedAccount.id().getAsLong();
        if (accounts.get(id).status() != AudienceAccountStatus.PENDING_VERIFICATION) {
            throw new ConflictException(AccountErrorCode.EMAIL_ALREADY_VERIFIED);
        }
        accounts.put(id, copyOf(verifiedAccount, id));
    }

    /** 비밀번호 저장을 그 쓰기 기록에도 남긴다 — 링크 저장소와 같은 기록을 넘기면 잠금 순서(계정 → 링크)를 볼 수 있다. */
    void recordPasswordSavesIn(WriteLog writeLog) {
        passwordSaveLog = writeLog;
    }

    @Override
    public void savePassword(AudienceAccount changedAccount) {
        long id = changedAccount.id().getAsLong();
        passwordSaveLog.record("비밀번호 저장 AUDIENCE:" + id);
        AudienceAccountStatus storedStatus = accounts.get(id).status();
        if (storedStatus != AudienceAccountStatus.ACTIVE && storedStatus != AudienceAccountStatus.SUSPENDED) {
            throw new ConflictException(AccountErrorCode.AUDIENCE_NOT_ACTIVE);
        }
        accounts.put(id, copyOf(changedAccount, id));
    }

    @Override
    public void savePhoneNumber(AudienceAccount changedAccount) {
        long id = changedAccount.id().getAsLong();
        accountWriteLog.record("전화번호 저장 AUDIENCE:" + id);
        if (anotherAccountTakesNextPhoneNumber) {
            anotherAccountTakesNextPhoneNumber = false;
            throw new ConflictException(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED, "전화번호 변경 경합(흉내)");
        }
        AudienceAccountStatus storedStatus = accounts.get(id).status();
        if (storedStatus == AudienceAccountStatus.SUSPENDED) {
            throw new ConflictException(AccountErrorCode.AUDIENCE_SUSPENDED);
        }
        if (storedStatus != AudienceAccountStatus.ACTIVE) {
            throw new ConflictException(AccountErrorCode.AUDIENCE_NOT_ACTIVE);
        }
        PhoneNumber newPhoneNumber = changedAccount.personalInformation().phoneNumber();
        boolean takenByAnother = registeredAccounts()
                .anyMatch(account -> account.id().getAsLong() != id
                        && account.personalInformation().phoneNumber().equals(newPhoneNumber));
        if (takenByAnother) {
            throw new ConflictException(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED);
        }
        AudienceAccount stored = accounts.get(id);
        accounts.put(id, AudienceAccount.restore(id, stored.personalInformation().withPhoneNumber(newPhoneNumber),
                stored.passwordHash().orElse(null), stored.signedUpAt(), stored.consentRecords(), stored.status(),
                stored.emailVerifiedAt().orElse(null), stored.suspensions()));
    }

    @Override
    public void addConsentRecord(long audienceAccountId, ConsentRecord consentRecord) {
        accountWriteLog.record("동의 기록 추가 AUDIENCE:" + audienceAccountId);
        AudienceAccount stored = accounts.get(audienceAccountId);
        List<ConsentRecord> consentRecords = new ArrayList<>(stored.consentRecords());
        consentRecords.add(consentRecord);
        accounts.put(audienceAccountId, AudienceAccount.restore(audienceAccountId, stored.personalInformation(),
                stored.passwordHash().orElse(null), stored.signedUpAt(), consentRecords, stored.status(),
                stored.emailVerifiedAt().orElse(null), stored.suspensions()));
    }

    private java.util.stream.Stream<AudienceAccount> registeredAccounts() {
        return accounts.values().stream().filter(account -> account.status() != AudienceAccountStatus.WITHDRAWN);
    }

    private static boolean sameEmail(EmailAddress left, EmailAddress right) {
        return left.value().toLowerCase(Locale.ROOT).equals(right.value().toLowerCase(Locale.ROOT));
    }

    private static AudienceAccount copyOf(AudienceAccount account, long id) {
        return AudienceAccount.restore(id, account.personalInformation(), account.passwordHash().orElse(null), account.signedUpAt(),
                new ArrayList<>(account.consentRecords()), account.status(), account.emailVerifiedAt().orElse(null),
                account.suspensions());
    }
}
