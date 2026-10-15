package com.ticketon.application.account.service;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAccountStatus;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.OAuthProvider;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.account.SignupConsentChoices;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 메모리 관객 계정 저장소에 상태별 계정을 넣는다. 이름은 한서윤, 비밀번호는 {@value #RAW_PASSWORD}다(비밀번호 없는 계정은 빼고).
 * 이용 정지(#125)·탈퇴(#83)는 저장 기능이 아직 없어 계정의 상태를 바꿔 저장소에 바로 넣는다.
 */
final class StoredAudienceAccounts {

    static final String RAW_PASSWORD = "seoyun1234";

    static final Instant SIGNUP_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    static final String SUSPENSION_REASON = "번호를 바꿔가며 1인 매수 제한을 넘는 예매";

    private final FakeAudienceAccountRepository repository;

    StoredAudienceAccounts(FakeAudienceAccountRepository repository) {
        this.repository = repository;
    }

    /** 가입만 하고 이메일 인증을 마치지 않은 계정. */
    long pending(String email, String phoneNumber) {
        AudiencePersonalInformation personalInformation = AudiencePersonalInformation.of("한서윤",
                LocalDate.of(1998, 4, 2), email, phoneNumber);
        AudienceAccount newAccount = AudienceAccount.signUp(personalInformation, FakePasswordHashPort.hashOf(RAW_PASSWORD),
                SignupConsentChoices.agreeingToAllRequired(false), PhoneNumberAvailability.AVAILABLE,
                EmailAvailability.AVAILABLE, SIGNUP_TIME);
        return repository.add(newAccount).id().getAsLong();
    }

    /** 이메일 인증까지 마친 계정. */
    long active(String email, String phoneNumber) {
        long id = pending(email, phoneNumber);
        AudienceAccount account = repository.findById(id).orElseThrow();
        account.verifyEmail(SIGNUP_TIME.plus(Duration.ofMinutes(5)));
        repository.saveEmailVerification(account);
        return id;
    }

    /**
     * 카카오·구글로 비밀번호 없이 가입하고, 제공자가 확인한 이메일이라 가입하며 바로 인증을 마친 계정(활동). 연결은 넣지 않는다 — 연결 저장소에
     * 따로 넣는다.
     */
    long withoutPassword(String email, String phoneNumber) {
        AudiencePersonalInformation personalInformation = AudiencePersonalInformation.of("한서윤",
                LocalDate.of(1998, 4, 2), email, phoneNumber);
        AudienceAccount newAccount = AudienceAccount.signUpThroughOAuth(OAuthProvider.KAKAO, personalInformation,
                SignupConsentChoices.agreeingToAllRequired(false), PhoneNumberAvailability.AVAILABLE,
                EmailAvailability.AVAILABLE, SIGNUP_TIME);
        newAccount.verifyEmail(SIGNUP_TIME);
        return repository.add(newAccount).id().getAsLong();
    }

    /** 활동 중인 계정을 관리자가 이용 정지한 것. */
    long suspended(String email, String phoneNumber) {
        long id = active(email, phoneNumber);
        AudienceAccount account = repository.findById(id).orElseThrow();
        account.suspend(SUSPENSION_REASON, SIGNUP_TIME.plus(Duration.ofDays(1)));
        repository.replace(account);
        return id;
    }

    /** 정지한 계정의 정지를 푼 것. */
    long lifted(String email, String phoneNumber) {
        long id = suspended(email, phoneNumber);
        AudienceAccount account = repository.findById(id).orElseThrow();
        account.lift(SIGNUP_TIME.plus(Duration.ofDays(2)));
        repository.replace(account);
        return id;
    }

    /** 활동 중이던 계정이 탈퇴한 것. */
    long withdrawn(String email, String phoneNumber) {
        long id = active(email, phoneNumber);
        AudienceAccount account = repository.findById(id).orElseThrow();
        repository.replace(AudienceAccount.restore(id, account.personalInformation(), account.passwordHash().orElse(null),
                account.signedUpAt(), account.consentRecords(), AudienceAccountStatus.WITHDRAWN,
                account.emailVerifiedAt().orElse(null), account.suspensions()));
        return id;
    }
}
