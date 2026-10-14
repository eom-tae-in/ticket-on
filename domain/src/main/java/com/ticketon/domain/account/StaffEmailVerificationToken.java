package com.ticketon.domain.account;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.policy.ProposedPolicy;
import java.time.Instant;
import java.util.Objects;

/**
 * 운영진 로그인 이메일 인증 링크 — 가입하면 그 이메일로 하나 보낸다(docs/01 "수집 정보 — 주최자 운영진" 이메일(인증)).
 * <p>
 * 관객 계정의 인증 링크({@link EmailVerificationToken})와 규칙이 같다. 링크 값은 추측할 수 없는 무작위 값
 * {@value EmailVerificationToken#LENGTH}자이고, 보낸 때부터 {@link ProposedPolicy#EMAIL_VERIFICATION_LINK_VALIDITY 유효 시간}
 * (확인 필요) 동안 열 수 있다. 인증한 뒤에도 지우지 않아, 같은 링크를 다시 열면 유효 시간과 상관없이 "이미 인증을 마친 계정"으로 안내한다.
 * 관객 계정과 계정 id를 따로 매기므로 링크도 따로 둔다 — 관객 링크로 운영진 로그인을 인증할 수 없고, 그 반대도 그렇다.
 *
 * @param value          링크에 담는 값
 * @param staffAccountId 인증할 운영진 로그인 계정
 * @param issuedAt       보낸 일시
 * @param expiresAt      이 순간부터 열어도 인증되지 않는다
 */
public record StaffEmailVerificationToken(String value, long staffAccountId, Instant issuedAt, Instant expiresAt) {

    public StaffEmailVerificationToken {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("인증 링크 값이 비어 있습니다");
        }
        if (staffAccountId < 1) {
            throw new IllegalArgumentException("계정 id는 1 이상이어야 합니다: " + staffAccountId);
        }
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("유효 시간이 끝나는 일시(" + expiresAt + ")는 보낸 일시(" + issuedAt + ") 뒤여야 합니다");
        }
    }

    /**
     * 막 가입한 계정에 보낼 링크를 만든다. 유효 시간은 지금부터 {@link ProposedPolicy#EMAIL_VERIFICATION_LINK_VALIDITY}다.
     *
     * @param value 추측할 수 없는 무작위 값
     */
    public static StaffEmailVerificationToken issue(String value, long staffAccountId, Instant now) {
        Objects.requireNonNull(now, "now");
        return new StaffEmailVerificationToken(value, staffAccountId, now,
                now.plus(ProposedPolicy.EMAIL_VERIFICATION_LINK_VALIDITY));
    }

    /** 그 순간에 유효 시간이 지났는지. 끝나는 일시 그 순간부터 지난 것이다. */
    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(expiresAt);
    }

    /**
     * 이 링크로 계정의 이메일 인증을 마친다. 인증 대기 계정이 활동 상태가 된다.
     *
     * @param account 이 링크가 가리키는 계정
     * @throws IllegalArgumentException 다른 계정이면
     * @throws RuleViolationException   인증 대기인데 유효 시간이 지났거나({@link AccountErrorCode#EMAIL_VERIFICATION_LINK_EXPIRED}),
     *                                  이미 인증했거나({@link AccountErrorCode#EMAIL_ALREADY_VERIFIED}) 탈퇴한 계정이면
     */
    public void verify(StaffAccount account, Instant now) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(now, "now");
        if (account.id().isEmpty() || account.id().getAsLong() != staffAccountId) {
            throw new IllegalArgumentException("이 인증 링크의 계정(" + staffAccountId + ")이 아닙니다: " + account.id());
        }
        if (account.status() == StaffAccountStatus.PENDING_VERIFICATION && isExpiredAt(now)) {
            throw new RuleViolationException(AccountErrorCode.EMAIL_VERIFICATION_LINK_EXPIRED,
                    "운영진 계정 " + staffAccountId + ", 끝난 일시 " + expiresAt);
        }
        account.verifyEmail(now);
    }

    @Override
    public String toString() {
        return "StaffEmailVerificationToken[staffAccountId=" + staffAccountId + ", issuedAt=" + issuedAt
                + ", expiresAt=" + expiresAt + "]";
    }
}
