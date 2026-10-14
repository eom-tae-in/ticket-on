package com.ticketon.domain.account;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.policy.ProposedPolicy;
import java.time.Instant;
import java.util.Objects;

/**
 * 이메일 인증 링크 — 가입하면 그 계정의 이메일로 하나 보낸다(docs/01 "가입 경로 — 직접 가입").
 * <p>
 * 링크에 담는 값은 추측할 수 없는 무작위 값이다. 서비스가 TokenGenerator로 {@value #LENGTH}자를 만들어 넘긴다
 * (URL 안전 문자 한 자에 6비트 — 192비트). 순차 번호처럼 추측할 수 있으면 남의 계정을 대신 인증할 수 있다.
 * <p>
 * 링크는 보낸 때부터 {@link ProposedPolicy#EMAIL_VERIFICATION_LINK_VALIDITY 유효 시간}(확인 필요) 동안 열 수 있다.
 * 인증한 뒤에도 링크를 지우지 않는다. 같은 링크를 다시 열면 유효 시간과 상관없이 "이미 인증을 마친 계정"으로 안내한다
 * — 메일의 링크를 두 번 누르는 일은 흔하고, 이미 인증한 사람에게 만료를 알릴 까닭이 없다.
 *
 * @param value             링크에 담는 값
 * @param audienceAccountId 인증할 관객 계정
 * @param issuedAt          보낸 일시
 * @param expiresAt         이 순간부터 열어도 인증되지 않는다
 */
public record EmailVerificationToken(String value, long audienceAccountId, Instant issuedAt, Instant expiresAt) {

    /** 링크에 담는 값의 길이(URL 안전 문자 수). */
    public static final int LENGTH = 32;

    public EmailVerificationToken {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("인증 링크 값이 비어 있습니다");
        }
        if (audienceAccountId < 1) {
            throw new IllegalArgumentException("계정 id는 1 이상이어야 합니다: " + audienceAccountId);
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
    public static EmailVerificationToken issue(String value, long audienceAccountId, Instant now) {
        Objects.requireNonNull(now, "now");
        return new EmailVerificationToken(value, audienceAccountId, now,
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
    public void verify(AudienceAccount account, Instant now) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(now, "now");
        if (account.id().isEmpty() || account.id().getAsLong() != audienceAccountId) {
            throw new IllegalArgumentException("이 인증 링크의 계정(" + audienceAccountId + ")이 아닙니다: " + account.id());
        }
        if (account.status() == AudienceAccountStatus.PENDING_VERIFICATION && isExpiredAt(now)) {
            throw new RuleViolationException(AccountErrorCode.EMAIL_VERIFICATION_LINK_EXPIRED,
                    "계정 " + audienceAccountId + ", 끝난 일시 " + expiresAt);
        }
        account.verifyEmail(now);
    }

    @Override
    public String toString() {
        return "EmailVerificationToken[audienceAccountId=" + audienceAccountId + ", issuedAt=" + issuedAt
                + ", expiresAt=" + expiresAt + "]";
    }
}
