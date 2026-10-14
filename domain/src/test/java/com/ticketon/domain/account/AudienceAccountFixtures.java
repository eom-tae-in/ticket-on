package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/** 관객 계정 테스트에서 함께 쓰는 값과 확인. */
final class AudienceAccountFixtures {

    /** 가입 일시 — 2026-10-15 10:00 (한국 시각). */
    static final Instant SIGNUP_TIME = koreanTime(2026, 10, 15, 10, 0);

    /** 1998-04-02생 — 가입일에 만 28세. */
    static final LocalDate ADULT_BIRTH_DATE = LocalDate.of(1998, 4, 2);

    private AudienceAccountFixtures() {
    }

    static Instant koreanTime(int year, int month, int day, int hour, int minute) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, minute));
    }

    static AudiencePersonalInformation personalInformationBornOn(LocalDate birthDate) {
        return AudiencePersonalInformation.of("한서윤", birthDate, "seoyun@example.com", "010-1234-5678");
    }

    static AudiencePersonalInformation adultPersonalInformation() {
        return personalInformationBornOn(ADULT_BIRTH_DATE);
    }

    /** 필수 셋에 동의하고 마케팅은 동의하지 않은 채 막 가입한 계정(인증 대기). */
    static AudienceAccount newlySignedUpAccount() {
        return AudienceAccount.signUp(adultPersonalInformation(), SignupConsentChoices.agreeingToAllRequired(false),
                PhoneNumberAvailability.AVAILABLE, SIGNUP_TIME);
    }

    /** 가입하고 이메일 인증까지 마친 계정(활동). */
    static AudienceAccount activeAccount() {
        AudienceAccount account = newlySignedUpAccount();
        account.verifyEmail(SIGNUP_TIME.plusSeconds(300));
        return account;
    }

    /** 규칙 위반으로 막히고, 그 오류 코드가 기대한 것인지 본다. */
    static void assertRejected(ThrowingCallable action, AccountErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
