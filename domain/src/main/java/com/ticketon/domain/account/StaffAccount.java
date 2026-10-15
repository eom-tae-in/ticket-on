package com.ticketon.domain.account;

import com.ticketon.domain.error.RuleViolationException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * 운영진 로그인 계정 — 주최자 쪽 로그인. 이름·이메일, 닉네임, 비밀번호 해시, 상태, 이메일 인증 여부
 * (docs/01 "계정 4종", "수집 정보 — 주최자 운영진").
 * <p>
 * <b>관객 계정과 별개다.</b> 같은 이메일로 관객 계정과 운영진 로그인을 둘 다 만들 수 있지만 서로 연결하지 않고, 운영진 로그인으로는
 * 예매할 수 없다(docs/01 "같은 이메일로 양쪽 가입"). 로그인 주체도 {@code STAFF:id}로 따로다({@link AccountRef}).
 * 주최자(단체)에 소속되는 일은 주최자 도메인이 한다 — 이 계정은 소속을 모른다.
 * <p>
 * 새 계정은 {@link #signUp}으로만 만든다. 이메일과 닉네임이 겹치면 막고, 인증 대기 상태로 시작한다. 이메일 인증을 마쳐야
 * 로그인할 수 있다. 닉네임은 바꿀 수 있다({@link #changeNickname}) — 참여 이력은 계정을 가리키므로 그대로 따라간다.
 * 비밀번호는 해시({@link PasswordHash})로만 갖는다. 원문의 최소 규칙은 {@link NewPassword}가 지킨다. 비밀번호는 비밀번호 찾기로만
 * 다시 정한다({@link #resetPassword}).
 * <p>
 * <b>탈퇴</b>({@link #withdraw})하면 이름 · 이메일 · 비밀번호 해시를 바로 파기한다 — 회원 탈퇴 시 법정 보존 대상 외 즉시 파기
 * (docs/11 "데이터 보관"). 운영진 로그인에는 법정 보존 대상(결제 기록 등)이 없다. <b>닉네임과 가입 일시는 남는다</b> — 닉네임은 공개
 * 프로필과 소속 이력 · 운영진 조작 기록에 남은 이름이라, 다른 사람이 같은 닉네임을 가져가지 않게 탈퇴한 뒤에도 쥔다(#15). 이메일은 풀려 같은
 * 이메일로 다시 가입할 수 있다. 소유자인 주최자가 남아 있으면 탈퇴할 수 없다는 규칙은 소속을 아는 주최자 도메인이 본다
 * ({@code StaffAffiliations#requireWithdrawable}) — 이 계정은 소속을 모른다.
 * 시각은 인자로 받는다.
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}(탈퇴하지 않은 계정)와 {@link #restoreWithdrawn}(탈퇴한 계정)을 쓴다. 저장 전 계정은 id가 없다.
 */
public final class StaffAccount {

    private final Long id;
    private final Instant signedUpAt;
    private StaffPersonalInformation personalInformation;
    private Nickname nickname;
    private PasswordHash passwordHash;
    private StaffAccountStatus status;
    private Instant emailVerifiedAt;
    private Instant withdrawnAt;

    private StaffAccount(Long id, StaffPersonalInformation personalInformation, Nickname nickname,
            PasswordHash passwordHash, Instant signedUpAt, StaffAccountStatus status, Instant emailVerifiedAt) {
        this.id = id;
        this.personalInformation = Objects.requireNonNull(personalInformation, "personalInformation");
        this.nickname = Objects.requireNonNull(nickname, "nickname");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.signedUpAt = Objects.requireNonNull(signedUpAt, "signedUpAt");
        this.status = Objects.requireNonNull(status, "status");
        this.emailVerifiedAt = emailVerifiedAt;
    }

    private StaffAccount(long id, Nickname nickname, Instant signedUpAt, Instant withdrawnAt) {
        this.id = id;
        this.nickname = Objects.requireNonNull(nickname, "nickname");
        this.signedUpAt = Objects.requireNonNull(signedUpAt, "signedUpAt");
        this.status = StaffAccountStatus.WITHDRAWN;
        this.withdrawnAt = Objects.requireNonNull(withdrawnAt, "withdrawnAt");
    }

    /**
     * 가입한다. 새 계정은 인증 대기 상태다.
     * <p>
     * 이메일 → 닉네임 순서로 보고, 처음 걸린 것으로 막는다.
     *
     * @param passwordHash          비밀번호 해시 — 최소 규칙을 지킨 원문({@link NewPassword})을 어댑터가 바꾼 값
     * @param emailAvailability     그 이메일을 쓰는 운영진 로그인이 있는지 — 저장소가 확인한 값. 관객 계정은 보지 않는다.
     * @param nicknameAvailability  그 닉네임을 쓰는 운영진 계정이 있는지 — 저장소가 확인한 값
     * @param now                   가입 일시
     * @throws RuleViolationException 가입된 이메일({@link AccountErrorCode#STAFF_EMAIL_ALREADY_REGISTERED}),
     *                                쓰고 있는 닉네임({@link AccountErrorCode#STAFF_NICKNAME_ALREADY_TAKEN})
     */
    public static StaffAccount signUp(StaffPersonalInformation personalInformation, Nickname nickname,
            PasswordHash passwordHash, EmailAvailability emailAvailability, NicknameAvailability nicknameAvailability,
            Instant now) {
        Objects.requireNonNull(personalInformation, "personalInformation");
        Objects.requireNonNull(nickname, "nickname");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(emailAvailability, "emailAvailability");
        Objects.requireNonNull(nicknameAvailability, "nicknameAvailability");
        Objects.requireNonNull(now, "now");
        if (emailAvailability != EmailAvailability.AVAILABLE) {
            throw new RuleViolationException(AccountErrorCode.STAFF_EMAIL_ALREADY_REGISTERED);
        }
        requireNicknameAvailable(nickname, nicknameAvailability);
        return new StaffAccount(null, personalInformation, nickname, passwordHash, now,
                StaffAccountStatus.PENDING_VERIFICATION, null);
    }

    /**
     * 저장소에서 읽은 값으로 탈퇴하지 않은 계정을 되살린다. 가입 규칙은 다시 보지 않는다.
     *
     * @param status          인증 대기 또는 활동. 탈퇴한 계정은 {@link #restoreWithdrawn}으로 되살린다.
     * @param emailVerifiedAt 이메일 인증 일시. 인증 전이면 null.
     * @throws IllegalArgumentException 탈퇴 상태이거나, 상태와 이메일 인증 여부가 맞지 않으면(인증 대기인데 인증 일시가 있는 등)
     */
    public static StaffAccount restore(long id, StaffPersonalInformation personalInformation, Nickname nickname,
            PasswordHash passwordHash, Instant signedUpAt, StaffAccountStatus status, Instant emailVerifiedAt) {
        requirePositiveId(id);
        Objects.requireNonNull(status, "status");
        if (status == StaffAccountStatus.WITHDRAWN) {
            throw new IllegalArgumentException("탈퇴한 계정은 개인정보 없이 restoreWithdrawn으로 되살립니다: " + id);
        }
        requireVerificationMatchesStatus(status, emailVerifiedAt);
        return new StaffAccount(id, personalInformation, nickname, passwordHash, signedUpAt, status, emailVerifiedAt);
    }

    /**
     * 저장소에서 읽은 값으로 탈퇴한 계정을 되살린다. 이름 · 이메일 · 비밀번호 해시는 탈퇴할 때 파기해 없다.
     *
     * @param nickname    탈퇴한 뒤에도 쥐고 있는 닉네임
     * @param withdrawnAt 탈퇴 일시
     */
    public static StaffAccount restoreWithdrawn(long id, Nickname nickname, Instant signedUpAt, Instant withdrawnAt) {
        requirePositiveId(id);
        return new StaffAccount(id, nickname, signedUpAt, withdrawnAt);
    }

    /**
     * 이메일 인증을 마친다. 인증 대기에서 활동으로 바뀐다.
     *
     * @throws RuleViolationException 이미 인증했거나({@link AccountErrorCode#EMAIL_ALREADY_VERIFIED})
     *                                탈퇴한 계정이면({@link AccountErrorCode#STAFF_WITHDRAWN})
     */
    public void verifyEmail(Instant now) {
        Objects.requireNonNull(now, "now");
        switch (status) {
            case PENDING_VERIFICATION -> {
                status = StaffAccountStatus.ACTIVE;
                emailVerifiedAt = now;
            }
            case ACTIVE -> throw new RuleViolationException(AccountErrorCode.EMAIL_ALREADY_VERIFIED);
            case WITHDRAWN -> throw new RuleViolationException(AccountErrorCode.STAFF_WITHDRAWN);
        }
    }

    /** 로그인할 수 있는지 — 이메일 인증을 마친 활동 중인 계정만. 인증 대기와 탈퇴는 로그인할 수 없다. */
    public boolean canLogIn() {
        return status == StaffAccountStatus.ACTIVE;
    }

    /**
     * 비밀번호 찾기를 할 수 있는지 — 로그인할 수 있는 계정({@link #canLogIn})만. 비밀번호 찾기는 로그인을 되찾는 일이라, 인증 대기·탈퇴처럼
     * 비밀번호가 맞아도 로그인할 수 없는 계정에는 재설정 링크를 보내지 않는다.
     */
    public boolean canResetPassword() {
        return canLogIn();
    }

    /**
     * 비밀번호를 새로 정한다 — 비밀번호 찾기로 받은 재설정 링크({@link PasswordResetToken})를 거쳐서만 부른다. 지금 비밀번호는 묻지 않는다
     * (잊어버려서 하는 일이다).
     *
     * @param newPasswordHash 새 비밀번호 해시 — 최소 규칙을 지킨 원문({@link NewPassword})을 어댑터가 바꾼 값
     * @throws RuleViolationException 인증 대기({@link AccountErrorCode#STAFF_NOT_ACTIVE})이거나
     *                                탈퇴한 계정({@link AccountErrorCode#STAFF_WITHDRAWN})이면
     */
    public void resetPassword(PasswordHash newPasswordHash) {
        Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        switch (status) {
            case ACTIVE -> passwordHash = newPasswordHash;
            case PENDING_VERIFICATION -> throw new RuleViolationException(AccountErrorCode.STAFF_NOT_ACTIVE);
            case WITHDRAWN -> throw new RuleViolationException(AccountErrorCode.STAFF_WITHDRAWN);
        }
    }

    /**
     * 닉네임을 바꾼다(docs/99 C "변경 가능"). 참여 이력은 계정을 가리키므로 그대로 따라간다.
     * <p>
     * 지금 닉네임과 같으면 아무것도 바꾸지 않는다 — 그 닉네임은 이 계정이 쥐고 있어 저장소는 쓰고 있다고 답하지만, 겹치는 것은 아니다.
     *
     * @param nicknameAvailability 새 닉네임을 쓰는 운영진 계정이 있는지 — 저장소가 확인한 값
     * @return 닉네임이 바뀌었으면 true, 지금 닉네임과 같아 그대로면 false
     * @throws RuleViolationException 다른 운영진이 쓰는 닉네임({@link AccountErrorCode#STAFF_NICKNAME_ALREADY_TAKEN}),
     *                                인증 대기 계정({@link AccountErrorCode#STAFF_NOT_ACTIVE}),
     *                                탈퇴한 계정({@link AccountErrorCode#STAFF_WITHDRAWN})
     */
    public boolean changeNickname(Nickname newNickname, NicknameAvailability nicknameAvailability) {
        Objects.requireNonNull(newNickname, "newNickname");
        Objects.requireNonNull(nicknameAvailability, "nicknameAvailability");
        return switch (status) {
            case ACTIVE -> replaceNickname(newNickname, nicknameAvailability);
            case PENDING_VERIFICATION -> throw new RuleViolationException(AccountErrorCode.STAFF_NOT_ACTIVE);
            case WITHDRAWN -> throw new RuleViolationException(AccountErrorCode.STAFF_WITHDRAWN);
        };
    }

    /**
     * 운영진 로그인을 탈퇴한다 — 이름 · 이메일 · 비밀번호 해시를 파기하고(docs/11 "데이터 보관 — 회원 탈퇴 시"), 닉네임과 가입 일시만 남긴다.
     * 탈퇴한 계정은 로그인할 수 없다. 소유자인 주최자가 남아 있으면 탈퇴할 수 없다는 규칙과 소속을 끝내는 일은 부르는 쪽이 먼저 한다
     * ({@code StaffAffiliations#requireWithdrawable}, {@code OrganizerStaff#leave}). 로그인 세션을 모두 끊는 일도 부르는 쪽이 한다.
     *
     * @param now 탈퇴 일시
     * @throws RuleViolationException 인증 대기 계정({@link AccountErrorCode#STAFF_NOT_ACTIVE}),
     *                                이미 탈퇴한 계정({@link AccountErrorCode#STAFF_WITHDRAWN})
     */
    public void withdraw(Instant now) {
        Objects.requireNonNull(now, "now");
        switch (status) {
            case ACTIVE -> {
                status = StaffAccountStatus.WITHDRAWN;
                withdrawnAt = now;
                personalInformation = null;
                passwordHash = null;
                emailVerifiedAt = null;
            }
            case PENDING_VERIFICATION -> throw new RuleViolationException(AccountErrorCode.STAFF_NOT_ACTIVE);
            case WITHDRAWN -> throw new RuleViolationException(AccountErrorCode.STAFF_WITHDRAWN);
        }
    }

    /** 탈퇴했는지. */
    public boolean isWithdrawn() {
        return status == StaffAccountStatus.WITHDRAWN;
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    /**
     * 이름 · 이메일.
     *
     * @throws IllegalStateException 탈퇴한 계정이면 — 탈퇴할 때 파기했다
     */
    public StaffPersonalInformation personalInformation() {
        requireNotWithdrawn("이름 · 이메일");
        return personalInformation;
    }

    public Nickname nickname() {
        return nickname;
    }

    /**
     * 비밀번호 해시. 원문은 갖고 있지 않다.
     *
     * @throws IllegalStateException 탈퇴한 계정이면 — 탈퇴할 때 파기했다
     */
    public PasswordHash passwordHash() {
        requireNotWithdrawn("비밀번호 해시");
        return passwordHash;
    }

    public Instant signedUpAt() {
        return signedUpAt;
    }

    public StaffAccountStatus status() {
        return status;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /** 이메일 인증 일시. 인증 전이거나 탈퇴했으면 비어 있다. */
    public Optional<Instant> emailVerifiedAt() {
        return Optional.ofNullable(emailVerifiedAt);
    }

    /** 탈퇴 일시. 탈퇴하지 않았으면 비어 있다. */
    public Optional<Instant> withdrawnAt() {
        return Optional.ofNullable(withdrawnAt);
    }

    private void requireNotWithdrawn(String destroyedItem) {
        if (isWithdrawn()) {
            throw new IllegalStateException("탈퇴한 계정의 " + destroyedItem + "은 파기했습니다: 계정 " + id);
        }
    }

    private static void requirePositiveId(long id) {
        if (id < 1) {
            throw new IllegalArgumentException("계정 id는 1 이상이어야 합니다: " + id);
        }
    }

    private boolean replaceNickname(Nickname newNickname, NicknameAvailability nicknameAvailability) {
        if (newNickname.equals(nickname)) {
            return false;
        }
        requireNicknameAvailable(newNickname, nicknameAvailability);
        nickname = newNickname;
        return true;
    }

    private static void requireNicknameAvailable(Nickname nickname, NicknameAvailability nicknameAvailability) {
        if (nicknameAvailability != NicknameAvailability.AVAILABLE) {
            throw new RuleViolationException(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN, "닉네임: " + nickname.value());
        }
    }

    private static void requireVerificationMatchesStatus(StaffAccountStatus status, Instant emailVerifiedAt) {
        boolean verified = emailVerifiedAt != null;
        if (verified != (status == StaffAccountStatus.ACTIVE)) {
            throw new IllegalArgumentException("상태(" + status + ")와 이메일 인증 여부(" + verified + ")가 맞지 않습니다");
        }
    }
}
