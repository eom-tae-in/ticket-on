package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

/** 운영진 로그인 계정 테스트에서 함께 쓰는 값과 확인. 사람은 docs/15의 박정훈(소유자)이다. */
final class StaffAccountFixtures {

    /** 가입 일시 — 2026-10-15 10:00 (한국 시각). */
    static final Instant SIGNUP_TIME = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    /** 비밀번호 해시 — 해시 방식은 어댑터가 정하므로 도메인 테스트에서는 아무 값이나 쓴다. */
    static final PasswordHash PASSWORD_HASH = new PasswordHash("{test}hashed-password");

    static final Nickname NICKNAME = Nickname.of("조용한고래");

    private StaffAccountFixtures() {
    }

    static StaffPersonalInformation personalInformation() {
        return StaffPersonalInformation.of("박정훈", "junghoon@example.com");
    }

    /** 막 가입한 계정(인증 대기). 닉네임은 조용한고래. */
    static StaffAccount newlySignedUpAccount() {
        return StaffAccount.signUp(personalInformation(), NICKNAME, PASSWORD_HASH, EmailAvailability.AVAILABLE,
                NicknameAvailability.AVAILABLE, SIGNUP_TIME);
    }

    /** 저장소에서 읽은 인증 대기 계정 — id가 있다. */
    static StaffAccount storedPendingAccount(long id) {
        return StaffAccount.restore(id, personalInformation(), NICKNAME, PASSWORD_HASH, SIGNUP_TIME,
                StaffAccountStatus.PENDING_VERIFICATION, null);
    }

    /** 저장소에서 읽은, 이메일 인증까지 마친 계정(활동). */
    static StaffAccount storedActiveAccount(long id) {
        return StaffAccount.restore(id, personalInformation(), NICKNAME, PASSWORD_HASH, SIGNUP_TIME,
                StaffAccountStatus.ACTIVE, SIGNUP_TIME.plusSeconds(300));
    }

    /** 저장소에서 읽은 탈퇴한 계정. */
    static StaffAccount storedWithdrawnAccount(long id) {
        return StaffAccount.restore(id, personalInformation(), NICKNAME, PASSWORD_HASH, SIGNUP_TIME,
                StaffAccountStatus.WITHDRAWN, SIGNUP_TIME.plusSeconds(300));
    }

    /** 규칙 위반으로 막히고, 그 오류 코드가 기대한 것인지 본다. */
    static void assertRejected(ThrowingCallable action, AccountErrorCode expectedCode) {
        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }
}
