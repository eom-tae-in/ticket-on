package com.ticketon.domain.account;

import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * 플랫폼 관리자 계정 — 이메일(로그인 아이디), 비밀번호 해시, 등급(docs/01 "계정 4종", "관리자 2등급").
 * <p>
 * <b>자유 가입이 없고 최고 관리자가 발급한다</b>({@link #issue}). 맨 처음 최고 관리자를 만드는 방법은 따로 정한다(#22).
 * 관객 계정·운영진 로그인과 별개이고, 로그인 주체도 {@code ADMIN:id}로 따로다({@link AccountRef}). 같은 이메일의 관객 계정이나 운영진 로그인이
 * 있어도 상관없다.
 * <p>
 * <b>프로필이 없다</b>(docs/01 "관객 · 관리자 · 검표 계정에는 프로필이 없습니다", docs/99 B "관리자 프로필") — 남이 관리자를 볼 일이 없어
 * 이름·닉네임을 받지 않는다. 관리자가 한 일은 관리자 조작 기록이 이 계정을 가리켜 남긴다.
 * <p>
 * 비밀번호는 해시({@link PasswordHash})로만 갖는다. 원문의 최소 규칙은 {@link NewPassword}가 지킨다.
 * 무엇을 할 수 있는지는 등급이 정한다({@link #requirePermission}). 시각은 인자로 받는다.
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 계정은 id가 없다.
 */
public final class AdminAccount {

    private final Long id;
    private final EmailAddress email;
    private final PasswordHash passwordHash;
    private final AdminGrade grade;
    private final Instant issuedAt;

    private AdminAccount(Long id, EmailAddress email, PasswordHash passwordHash, AdminGrade grade, Instant issuedAt) {
        this.id = id;
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.grade = Objects.requireNonNull(grade, "grade");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
    }

    /**
     * 관리자 계정을 발급한다 — 최고 관리자만 한다(docs/01 "계정 4종", "관리자 2등급").
     * <p>
     * 발급하는 사람의 권한 → 이메일 순서로 보고, 처음 걸린 것으로 막는다.
     *
     * @param issuer            발급하는 관리자 — 저장소에서 읽은 계정. 지금 등급으로 권한을 본다.
     * @param email             새 관리자의 이메일 — 로그인 아이디
     * @param passwordHash      새 관리자의 비밀번호 해시 — 최소 규칙을 지킨 원문({@link NewPassword})을 어댑터가 바꾼 값
     * @param grade             새 관리자의 등급
     * @param emailAvailability 그 이메일을 쓰는 관리자 계정이 있는지 — 저장소가 확인한 값. 관객 계정·운영진 로그인은 보지 않는다.
     * @param now               발급 일시
     * @throws PermissionDeniedException 발급하는 사람이 최고 관리자가 아니면({@link AccountErrorCode#SUPER_ADMIN_ONLY_ACTION})
     * @throws RuleViolationException    같은 이메일의 관리자 계정이 있으면({@link AccountErrorCode#ADMIN_EMAIL_ALREADY_REGISTERED})
     */
    public static AdminAccount issue(AdminAccount issuer, EmailAddress email, PasswordHash passwordHash, AdminGrade grade,
            EmailAvailability emailAvailability, Instant now) {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(grade, "grade");
        Objects.requireNonNull(emailAvailability, "emailAvailability");
        Objects.requireNonNull(now, "now");
        issuer.requirePermission(AdminAction.ISSUE_ADMIN_ACCOUNT);
        if (emailAvailability != EmailAvailability.AVAILABLE) {
            throw new RuleViolationException(AccountErrorCode.ADMIN_EMAIL_ALREADY_REGISTERED);
        }
        return new AdminAccount(null, email, passwordHash, grade, now);
    }

    /** 저장소에서 읽은 값으로 계정을 되살린다. 발급 규칙은 다시 보지 않는다. */
    public static AdminAccount restore(long id, EmailAddress email, PasswordHash passwordHash, AdminGrade grade,
            Instant issuedAt) {
        if (id < 1) {
            throw new IllegalArgumentException("계정 id는 1 이상이어야 합니다: " + id);
        }
        return new AdminAccount(id, email, passwordHash, grade, issuedAt);
    }

    /** 이 관리자가 그 일을 할 수 있는지 — 등급으로 정한다({@link AdminGrade#can}). */
    public boolean can(AdminAction action) {
        return grade.can(action);
    }

    /**
     * 그 일을 할 수 있는 관리자인지 확인한다.
     *
     * @throws PermissionDeniedException 최고 관리자만 하는 일인데 관리자이면({@link AccountErrorCode#SUPER_ADMIN_ONLY_ACTION})
     */
    public void requirePermission(AdminAction action) {
        Objects.requireNonNull(action, "action");
        if (!can(action)) {
            throw new PermissionDeniedException(AccountErrorCode.SUPER_ADMIN_ONLY_ACTION,
                    "관리자 계정 " + id + "(" + grade + ")이 " + action + "을 하려 함");
        }
    }

    /**
     * 로그인 주체({@code ADMIN:id}).
     *
     * @throws IllegalStateException 저장 전이라 id가 없으면
     */
    public AccountRef accountRef() {
        if (id == null) {
            throw new IllegalStateException("저장하지 않은 관리자 계정에는 로그인 주체가 없습니다");
        }
        return AccountRef.of(AccountKind.ADMIN, id);
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public EmailAddress email() {
        return email;
    }

    /** 비밀번호 해시. 원문은 갖고 있지 않다. */
    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public AdminGrade grade() {
        return grade;
    }

    public Instant issuedAt() {
        return issuedAt;
    }
}
