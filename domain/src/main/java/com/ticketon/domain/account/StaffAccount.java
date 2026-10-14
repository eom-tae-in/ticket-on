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
 * 비밀번호는 해시({@link PasswordHash})로만 갖는다. 원문의 최소 규칙은 {@link NewPassword}가 지킨다.
 * 시각은 인자로 받는다.
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 계정은 id가 없다.
 */
public final class StaffAccount {

    private final Long id;
    private final StaffPersonalInformation personalInformation;
    private final PasswordHash passwordHash;
    private final Instant signedUpAt;
    private Nickname nickname;
    private StaffAccountStatus status;
    private Instant emailVerifiedAt;

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
     * 저장소에서 읽은 값으로 계정을 되살린다. 가입 규칙은 다시 보지 않는다.
     *
     * @param emailVerifiedAt 이메일 인증 일시. 인증 전이면 null.
     * @throws IllegalArgumentException 상태와 이메일 인증 여부가 맞지 않으면(인증 대기인데 인증 일시가 있는 등)
     */
    public static StaffAccount restore(long id, StaffPersonalInformation personalInformation, Nickname nickname,
            PasswordHash passwordHash, Instant signedUpAt, StaffAccountStatus status, Instant emailVerifiedAt) {
        if (id < 1) {
            throw new IllegalArgumentException("계정 id는 1 이상이어야 합니다: " + id);
        }
        Objects.requireNonNull(status, "status");
        requireVerificationMatchesStatus(status, emailVerifiedAt);
        return new StaffAccount(id, personalInformation, nickname, passwordHash, signedUpAt, status, emailVerifiedAt);
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

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public StaffPersonalInformation personalInformation() {
        return personalInformation;
    }

    public Nickname nickname() {
        return nickname;
    }

    /** 비밀번호 해시. 원문은 갖고 있지 않다. */
    public PasswordHash passwordHash() {
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

    /** 이메일 인증 일시. 인증 전이면 비어 있다. */
    public Optional<Instant> emailVerifiedAt() {
        return Optional.ofNullable(emailVerifiedAt);
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
        boolean mismatch = switch (status) {
            case PENDING_VERIFICATION -> verified;
            case ACTIVE -> !verified;
            case WITHDRAWN -> false;
        };
        if (mismatch) {
            throw new IllegalArgumentException("상태(" + status + ")와 이메일 인증 여부(" + verified + ")가 맞지 않습니다");
        }
    }
}
