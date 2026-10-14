package com.ticketon.domain.account;

import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.policy.ProposedPolicy;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 비밀번호 재설정 링크 — 비밀번호 찾기를 하면 그 계정의 이메일로 하나 보낸다(docs/01 "같은 이메일로 양쪽 가입"의 비밀번호 찾기).
 * <p>
 * 한 이메일에 관객 계정과 운영진 로그인이 둘 다 있을 수 있어 비밀번호 찾기에서 어느 쪽인지 고른다({@link #RESETTABLE_KINDS}).
 * 링크는 고른 쪽 계정 하나({@link AccountRef})만 가리킨다 — 관객 쪽 링크로 운영진 로그인의 비밀번호를 바꿀 수 없고, 그 반대도 그렇다.
 * <p>
 * 링크에 담는 값은 추측할 수 없는 무작위 값이다. 서비스가 TokenGenerator로 {@value #LENGTH}자를 만들어 넘긴다
 * (URL 안전 문자 한 자에 6비트 — 192비트). 추측할 수 있으면 남의 계정 비밀번호를 바꿀 수 있다.
 * <ul>
 *   <li>보낸 때부터 {@link ProposedPolicy#PASSWORD_RESET_LINK_VALIDITY 유효 시간}(확인 필요) 동안만 쓸 수 있다. 만료는 저장하지 않고
 *       시각으로 계산한다.</li>
 *   <li>한 번만 쓴다. 비밀번호를 바꾸면 쓴 일시가 남고, 같은 링크로 다시 바꿀 수 없다.</li>
 *   <li>비밀번호 찾기를 다시 하면 새 링크가 가고, 아직 쓰지 않은 이전 링크는 대체된다(대체된 일시가 남는다). 그래서 쓸 수 있는 링크는
 *       계정마다 많아야 하나다. 대체는 저장소가 새 링크를 저장하기 전에 한꺼번에 한다.</li>
 * </ul>
 * 쓴 링크·대체된 링크도 지우지 않는다. 다시 열면 '올바르지 않은 링크' 대신 왜 쓸 수 없는지 알려 준다.
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다.
 */
public final class PasswordResetToken {

    /** 링크에 담는 값의 길이(URL 안전 문자 수). */
    public static final int LENGTH = 32;

    /**
     * 비밀번호 찾기를 할 수 있는 계정 종류 — 관객과 주최자(운영진 로그인) 중 고른다(docs/13 "로그인 / 회원가입 — 비밀번호 찾기").
     * 검표 계정은 비밀번호를 운영진이 재발급하고(docs/01 "검표 계정"), 관리자 계정은 최고 관리자가 발급한다.
     */
    public static final Set<AccountKind> RESETTABLE_KINDS = Set.of(AccountKind.AUDIENCE, AccountKind.STAFF);

    private final String value;
    private final AccountRef account;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private final Instant replacedAt;
    private Instant usedAt;

    private PasswordResetToken(String value, AccountRef account, Instant issuedAt, Instant expiresAt, Instant usedAt,
            Instant replacedAt) {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("재설정 링크 값이 비어 있습니다");
        }
        requireResettable(Objects.requireNonNull(account, "account").kind());
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("유효 시간이 끝나는 일시(" + expiresAt + ")는 보낸 일시(" + issuedAt + ") 뒤여야 합니다");
        }
        if (usedAt != null && replacedAt != null) {
            throw new IllegalArgumentException("쓴 링크는 대체되지 않고, 대체된 링크는 쓸 수 없습니다");
        }
        this.value = value;
        this.account = account;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.usedAt = usedAt;
        this.replacedAt = replacedAt;
    }

    /**
     * 비밀번호 찾기를 한 계정에 보낼 링크를 만든다. 유효 시간은 지금부터 {@link ProposedPolicy#PASSWORD_RESET_LINK_VALIDITY}다.
     *
     * @param value   추측할 수 없는 무작위 값
     * @param account 비밀번호를 바꿀 계정 — 관객 계정 또는 운영진 로그인
     * @throws IllegalArgumentException 비밀번호 찾기를 하지 않는 계정 종류(관리자·검표)면
     */
    public static PasswordResetToken issue(String value, AccountRef account, Instant now) {
        Objects.requireNonNull(now, "now");
        return new PasswordResetToken(value, account, now, now.plus(ProposedPolicy.PASSWORD_RESET_LINK_VALIDITY), null, null);
    }

    /**
     * 저장소에서 읽은 값으로 링크를 되살린다.
     *
     * @param usedAt     비밀번호를 바꾼 일시. 아직 쓰지 않았으면 null.
     * @param replacedAt 새 링크를 보내 대체된 일시. 대체되지 않았으면 null.
     * @throws IllegalArgumentException 쓴 일시와 대체된 일시가 둘 다 있으면
     */
    public static PasswordResetToken restore(String value, AccountRef account, Instant issuedAt, Instant expiresAt,
            Instant usedAt, Instant replacedAt) {
        return new PasswordResetToken(value, account, issuedAt, expiresAt, usedAt, replacedAt);
    }

    /**
     * 비밀번호 찾기를 하는 계정 종류인지 확인한다.
     *
     * @throws IllegalArgumentException 관객·운영진 로그인이 아니면
     */
    public static AccountKind requireResettable(AccountKind kind) {
        Objects.requireNonNull(kind, "kind");
        if (!RESETTABLE_KINDS.contains(kind)) {
            throw new IllegalArgumentException("비밀번호 찾기는 관객 계정과 운영진 로그인만 합니다: " + kind);
        }
        return kind;
    }

    /** 그 순간에 유효 시간이 지났는지. 끝나는 일시 그 순간부터 지난 것이다. */
    public boolean isExpiredAt(Instant now) {
        Objects.requireNonNull(now, "now");
        return !now.isBefore(expiresAt);
    }

    /**
     * 이 링크로 관객 계정의 비밀번호를 바꾼다. 바꾸면 이 링크는 쓴 링크가 된다.
     *
     * @param account         이 링크가 가리키는 관객 계정
     * @param newPasswordHash 새 비밀번호 해시
     * @throws IllegalArgumentException 이 링크가 가리키는 계정이 아니면(운영진 로그인을 가리키는 링크 포함)
     * @throws RuleViolationException   이미 썼거나({@link AccountErrorCode#PASSWORD_RESET_LINK_ALREADY_USED}), 새 링크로
     *                                  대체됐거나({@link AccountErrorCode#PASSWORD_RESET_LINK_REPLACED}), 유효 시간이
     *                                  지났거나({@link AccountErrorCode#PASSWORD_RESET_LINK_EXPIRED}), 계정이 비밀번호를 바꿀 수 없는
     *                                  상태면({@link AudienceAccount#resetPassword})
     */
    public void resetPassword(AudienceAccount account, PasswordHash newPasswordHash, Instant now) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        requirePointsTo(AccountKind.AUDIENCE, account.id());
        requireUsableAt(now);
        account.resetPassword(newPasswordHash);
        usedAt = now;
    }

    /**
     * 이 링크로 운영진 로그인의 비밀번호를 바꾼다. 바꾸면 이 링크는 쓴 링크가 된다.
     *
     * @param account         이 링크가 가리키는 운영진 로그인 계정
     * @param newPasswordHash 새 비밀번호 해시
     * @throws IllegalArgumentException 이 링크가 가리키는 계정이 아니면(관객 계정을 가리키는 링크 포함)
     * @throws RuleViolationException   이미 썼거나({@link AccountErrorCode#PASSWORD_RESET_LINK_ALREADY_USED}), 새 링크로
     *                                  대체됐거나({@link AccountErrorCode#PASSWORD_RESET_LINK_REPLACED}), 유효 시간이
     *                                  지났거나({@link AccountErrorCode#PASSWORD_RESET_LINK_EXPIRED}), 계정이 비밀번호를 바꿀 수 없는
     *                                  상태면({@link StaffAccount#resetPassword})
     */
    public void resetPassword(StaffAccount account, PasswordHash newPasswordHash, Instant now) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        requirePointsTo(AccountKind.STAFF, account.id());
        requireUsableAt(now);
        account.resetPassword(newPasswordHash);
        usedAt = now;
    }

    public String value() {
        return value;
    }

    /** 비밀번호를 바꿀 계정 — 관객 계정 또는 운영진 로그인. */
    public AccountRef account() {
        return account;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    /** 이 순간부터 열어도 비밀번호를 바꿀 수 없다. */
    public Instant expiresAt() {
        return expiresAt;
    }

    /** 이 링크로 비밀번호를 바꾼 일시. 아직 쓰지 않았으면 비어 있다. */
    public Optional<Instant> usedAt() {
        return Optional.ofNullable(usedAt);
    }

    /** 새 링크를 보내 이 링크가 대체된 일시. 대체되지 않았으면 비어 있다. */
    public Optional<Instant> replacedAt() {
        return Optional.ofNullable(replacedAt);
    }

    /**
     * 쓴 링크인지 → 대체된 링크인지 → 유효 시간이 지났는지 순서로 본다. 쓴 링크는 유효 시간이 지난 뒤에 열어도 '이미 사용한 링크'로 알린다
     * — 비밀번호를 바꾼 사람이 메일의 링크를 다시 눌렀을 때 만료보다 그쪽이 맞는 안내다.
     */
    private void requireUsableAt(Instant now) {
        Objects.requireNonNull(now, "now");
        if (usedAt != null) {
            throw new RuleViolationException(AccountErrorCode.PASSWORD_RESET_LINK_ALREADY_USED, account.principalName());
        }
        if (replacedAt != null) {
            throw new RuleViolationException(AccountErrorCode.PASSWORD_RESET_LINK_REPLACED, account.principalName());
        }
        if (isExpiredAt(now)) {
            throw new RuleViolationException(AccountErrorCode.PASSWORD_RESET_LINK_EXPIRED,
                    account.principalName() + ", 끝난 일시 " + expiresAt);
        }
    }

    private void requirePointsTo(AccountKind kind, OptionalLong accountId) {
        if (account.kind() != kind || accountId.isEmpty() || accountId.getAsLong() != account.id()) {
            throw new IllegalArgumentException("이 재설정 링크의 계정(" + account.principalName() + ")이 아닙니다: " + kind + " "
                    + accountId);
        }
    }

    @Override
    public String toString() {
        return "PasswordResetToken[account=" + account.principalName() + ", issuedAt=" + issuedAt + ", expiresAt="
                + expiresAt + ", usedAt=" + usedAt + ", replacedAt=" + replacedAt + "]";
    }
}
