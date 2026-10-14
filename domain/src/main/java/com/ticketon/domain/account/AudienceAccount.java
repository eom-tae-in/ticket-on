package com.ticketon.domain.account;

import com.ticketon.common.AgeCalculator;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.policy.QuantityPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 관객 계정 — 가입 정보 넷, 비밀번호 해시, 동의 기록, 상태, 이메일 인증 여부, 이용 정지 이력(docs/01 "수집 정보 — 사용자",
 * "가입 경로 — 직접 가입", "약관과 동의", "관객 이용 정지").
 * <p>
 * 새 계정은 {@link #signUp}으로만 만든다. 가입 규칙 넷을 여기서 지킨다.
 * <ul>
 *   <li>가입일(한국 날짜)의 만 나이가 14세 미만이면 막는다. 동의 항목 "만 14세 이상입니다"에 체크했어도 생년월일로 다시 본다
 *       — 생년월일 차단과 동의 항목을 함께 두는 것이 연령 허위 기재를 막는 합리적 장치다(docs/01, docs/99 A-3).</li>
 *   <li>필수 동의 셋 중 하나라도 빠지면 막는다. 마케팅 수신은 동의하지 않아도 된다.</li>
 *   <li>같은 번호로 가입한 계정이 있거나 차단된 번호면 막는다 — 번호 하나에 계정 하나.</li>
 *   <li>같은 이메일의 관객 계정이 있으면 막는다 — 이메일이 로그인 아이디다.</li>
 * </ul>
 * 비밀번호는 해시({@link PasswordHash})로만 갖는다. 원문의 최소 규칙은 {@link NewPassword}가 지킨다. 비밀번호는 비밀번호 찾기로만
 * 다시 정한다({@link #resetPassword}).
 * 상태는 의도가 드러나는 메서드로만 바꾼다. 시각은 인자로 받는다.
 * <p>
 * <b>관객에게 거는 제재는 이용 정지 하나뿐이다</b>({@link #suspend}, {@link #lift}). 정지 중에는 새 예매·변경·신고만 막히고
 * 로그인·기존 티켓 보기·QR·취소는 된다({@link #can}).
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 계정은 id가 없다.
 */
public final class AudienceAccount {

    private final Long id;
    private final AudiencePersonalInformation personalInformation;
    private final Instant signedUpAt;
    private final List<ConsentRecord> consentRecords;
    private final List<AudienceSuspension> suspensions;
    private PasswordHash passwordHash;
    private AudienceAccountStatus status;
    private Instant emailVerifiedAt;

    private AudienceAccount(Long id, AudiencePersonalInformation personalInformation, PasswordHash passwordHash,
            Instant signedUpAt, List<ConsentRecord> consentRecords, AudienceAccountStatus status, Instant emailVerifiedAt,
            List<AudienceSuspension> suspensions) {
        this.id = id;
        this.personalInformation = Objects.requireNonNull(personalInformation, "personalInformation");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.signedUpAt = Objects.requireNonNull(signedUpAt, "signedUpAt");
        this.consentRecords = new ArrayList<>(List.copyOf(consentRecords));
        this.status = Objects.requireNonNull(status, "status");
        this.emailVerifiedAt = emailVerifiedAt;
        this.suspensions = new ArrayList<>(List.copyOf(suspensions));
    }

    /**
     * 가입한다. 새 계정은 인증 대기 상태이고, 네 동의 항목의 동의 여부를 가입 일시로 기록한다.
     * <p>
     * 규칙은 만 나이 → 필수 동의 → 전화번호 → 이메일 순서로 보고, 처음 걸린 것으로 막는다.
     *
     * @param passwordHash            비밀번호 해시 — 최소 규칙을 지킨 원문({@link NewPassword})을 어댑터가 바꾼 값
     * @param phoneNumberAvailability 가입 정보의 전화번호를 쓸 수 있는지 — 저장소가 확인한 값
     * @param emailAvailability       가입 정보의 이메일을 쓸 수 있는지 — 저장소가 확인한 값
     * @param now                     가입 일시. 만 나이는 이 순간의 한국 날짜로 센다.
     * @throws RuleViolationException   만 14세 미만({@link AccountErrorCode#AUDIENCE_UNDER_MINIMUM_AGE}),
     *                                  필수 동의 누락({@link AccountErrorCode#SIGNUP_REQUIRED_CONSENT_MISSING}),
     *                                  가입된 번호 또는 차단된 번호({@link AccountErrorCode#PHONE_NUMBER_ALREADY_REGISTERED}),
     *                                  가입된 이메일({@link AccountErrorCode#AUDIENCE_EMAIL_ALREADY_REGISTERED})
     * @throws IllegalArgumentException 생년월일이 가입일보다 늦으면
     */
    public static AudienceAccount signUp(AudiencePersonalInformation personalInformation, PasswordHash passwordHash,
            SignupConsentChoices consentChoices, PhoneNumberAvailability phoneNumberAvailability,
            EmailAvailability emailAvailability, Instant now) {
        Objects.requireNonNull(personalInformation, "personalInformation");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(consentChoices, "consentChoices");
        Objects.requireNonNull(phoneNumberAvailability, "phoneNumberAvailability");
        Objects.requireNonNull(emailAvailability, "emailAvailability");
        Objects.requireNonNull(now, "now");
        requireMinimumAge(personalInformation.birthDate(), now);
        requireAllRequiredConsents(consentChoices);
        requirePhoneNumberAvailable(phoneNumberAvailability);
        requireEmailAvailable(emailAvailability);
        return new AudienceAccount(null, personalInformation, passwordHash, now, consentChoices.recordsAt(now),
                AudienceAccountStatus.PENDING_VERIFICATION, null, List.of());
    }

    /**
     * 저장소에서 읽은 값으로 계정을 되살린다. 가입 규칙은 다시 보지 않는다.
     *
     * @param consentRecords  일어난 순서대로의 동의 기록
     * @param emailVerifiedAt 이메일 인증 일시. 인증 전이면 null.
     * @param suspensions     일어난 순서대로의 이용 정지 이력
     * @throws IllegalArgumentException 상태와 이메일 인증 여부가 맞지 않거나(인증 대기인데 인증 일시가 있는 등),
     *                                  상태와 정지 이력이 맞지 않으면(이용 정지인데 풀리지 않은 정지가 없는 등)
     */
    public static AudienceAccount restore(long id, AudiencePersonalInformation personalInformation,
            PasswordHash passwordHash, Instant signedUpAt, List<ConsentRecord> consentRecords,
            AudienceAccountStatus status, Instant emailVerifiedAt, List<AudienceSuspension> suspensions) {
        if (id < 1) {
            throw new IllegalArgumentException("계정 id는 1 이상이어야 합니다: " + id);
        }
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(suspensions, "suspensions");
        requireVerificationMatchesStatus(status, emailVerifiedAt);
        requireSuspensionsMatchStatus(status, suspensions);
        return new AudienceAccount(id, personalInformation, passwordHash, signedUpAt, consentRecords, status,
                emailVerifiedAt, suspensions);
    }

    /**
     * 이메일 인증을 마친다. 인증 대기에서 활동으로 바뀐다.
     *
     * @throws RuleViolationException 이미 인증했거나({@link AccountErrorCode#EMAIL_ALREADY_VERIFIED}) 탈퇴한 계정이면
     */
    public void verifyEmail(Instant now) {
        Objects.requireNonNull(now, "now");
        switch (status) {
            case PENDING_VERIFICATION -> {
                status = AudienceAccountStatus.ACTIVE;
                emailVerifiedAt = now;
            }
            case ACTIVE, SUSPENDED -> throw new RuleViolationException(AccountErrorCode.EMAIL_ALREADY_VERIFIED);
            case WITHDRAWN -> throw new RuleViolationException(AccountErrorCode.AUDIENCE_WITHDRAWN);
        }
    }

    /**
     * 이용 정지한다 — 관리자만 건다(docs/01 "관객 이용 정지"). 사유와 시각을 남긴다.
     * <p>
     * 활동 중인 계정에만 건다. 쓰는 경우가 번호를 바꿔가며 매수 제한을 넘는 예매, 반복되는 허위 신고처럼 로그인해서 한 일이기 때문이다.
     *
     * @param reason 정지 사유
     * @throws IllegalArgumentException 사유가 비었으면
     * @throws RuleViolationException   이미 정지됐거나({@link AccountErrorCode#AUDIENCE_ALREADY_SUSPENDED}),
     *                                  인증 대기거나({@link AccountErrorCode#AUDIENCE_NOT_ACTIVE}), 탈퇴한 계정이면
     */
    public void suspend(String reason, Instant now) {
        Objects.requireNonNull(now, "now");
        switch (status) {
            case ACTIVE -> {
                suspensions.add(AudienceSuspension.startingAt(reason, now));
                status = AudienceAccountStatus.SUSPENDED;
            }
            case SUSPENDED -> throw new RuleViolationException(AccountErrorCode.AUDIENCE_ALREADY_SUSPENDED);
            case PENDING_VERIFICATION -> throw new RuleViolationException(AccountErrorCode.AUDIENCE_NOT_ACTIVE);
            case WITHDRAWN -> throw new RuleViolationException(AccountErrorCode.AUDIENCE_WITHDRAWN);
        }
    }

    /**
     * 이용 정지를 해제한다. 활동 상태로 돌아가고, 정지 이력에 해제 시각이 남는다.
     *
     * @throws IllegalArgumentException 해제 시각이 정지 시각보다 앞서면
     * @throws RuleViolationException   정지된 계정이 아니거나({@link AccountErrorCode#AUDIENCE_NOT_SUSPENDED}) 탈퇴한 계정이면
     */
    public void lift(Instant now) {
        Objects.requireNonNull(now, "now");
        switch (status) {
            case SUSPENDED -> {
                int currentIndex = suspensions.size() - 1;
                suspensions.set(currentIndex, suspensions.get(currentIndex).liftAt(now));
                status = AudienceAccountStatus.ACTIVE;
            }
            case PENDING_VERIFICATION, ACTIVE -> throw new RuleViolationException(AccountErrorCode.AUDIENCE_NOT_SUSPENDED);
            case WITHDRAWN -> throw new RuleViolationException(AccountErrorCode.AUDIENCE_WITHDRAWN);
        }
    }

    /**
     * 비밀번호를 새로 정한다 — 비밀번호 찾기로 받은 재설정 링크({@link PasswordResetToken})를 거쳐서만 부른다. 지금 비밀번호는 묻지 않는다
     * (잊어버려서 하는 일이다).
     * <p>
     * 로그인할 수 있는 계정({@link AudienceAction#RESET_PASSWORD} — 활동·이용 정지)만 바꾼다. 이용 정지 중에도 로그인은 되므로
     * 로그인을 되찾는 일도 막지 않는다.
     *
     * @param newPasswordHash 새 비밀번호 해시 — 최소 규칙을 지킨 원문({@link NewPassword})을 어댑터가 바꾼 값
     * @throws RuleViolationException 인증 대기({@link AccountErrorCode#AUDIENCE_NOT_ACTIVE})이거나
     *                                탈퇴한 계정({@link AccountErrorCode#AUDIENCE_WITHDRAWN})이면
     */
    public void resetPassword(PasswordHash newPasswordHash) {
        Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        switch (status) {
            case ACTIVE, SUSPENDED -> passwordHash = newPasswordHash;
            case PENDING_VERIFICATION -> throw new RuleViolationException(AccountErrorCode.AUDIENCE_NOT_ACTIVE);
            case WITHDRAWN -> throw new RuleViolationException(AccountErrorCode.AUDIENCE_WITHDRAWN);
        }
    }

    /**
     * 지금 상태에서 그 일을 할 수 있는지.
     * <p>
     * 활동 중이면 전부 된다. 이용 정지면 새 예매·변경·신고만 막히고 로그인·기존 티켓 보기·QR·취소는 된다 — 산 표는 뺏지 않는다
     * (docs/01 "관객 이용 정지"). 인증 대기와 탈퇴는 로그인부터 할 수 없으므로 아무것도 할 수 없다.
     */
    public boolean can(AudienceAction action) {
        Objects.requireNonNull(action, "action");
        return switch (status) {
            case ACTIVE -> true;
            case SUSPENDED -> !action.isRestrictedWhileSuspended();
            case PENDING_VERIFICATION, WITHDRAWN -> false;
        };
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public AudiencePersonalInformation personalInformation() {
        return personalInformation;
    }

    /** 비밀번호 해시. 원문은 갖고 있지 않다. */
    public PasswordHash passwordHash() {
        return passwordHash;
    }

    public Instant signedUpAt() {
        return signedUpAt;
    }

    public AudienceAccountStatus status() {
        return status;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    /** 지금 이용 정지 중인지. 정지 중에도 로그인은 되므로 화면이 이 값으로 정지 여부를 알려 준다(docs/01 "관객 이용 정지"). */
    public boolean isSuspended() {
        return status == AudienceAccountStatus.SUSPENDED;
    }

    /** 이메일 인증 일시. 인증 전이면 비어 있다. */
    public Optional<Instant> emailVerifiedAt() {
        return Optional.ofNullable(emailVerifiedAt);
    }

    /** 동의 기록 전부 — 동의를 받았다는 근거. 일어난 순서대로. */
    public List<ConsentRecord> consentRecords() {
        return List.copyOf(consentRecords);
    }

    /** 그 항목의 가장 최근 동의 기록 — 그 항목의 지금 상태. 일시가 같으면 나중에 남긴 줄이다. */
    public Optional<ConsentRecord> latestConsent(ConsentItem item) {
        Objects.requireNonNull(item, "item");
        return consentRecords.stream()
                .filter(record -> record.item() == item)
                .reduce((latestSoFar, next) -> next.recordedAt().isBefore(latestSoFar.recordedAt()) ? latestSoFar : next);
    }

    /** 지금 마케팅 수신에 동의한 상태인지. 관리자의 마케팅 발송은 동의한 사람에게만 간다(docs/12). */
    public boolean isMarketingAgreed() {
        return latestConsent(ConsentItem.MARKETING).map(ConsentRecord::agreed).orElse(false);
    }

    /** 이용 정지 이력 전부 — 해제한 정지도 사유와 함께 남는다. 일어난 순서대로. */
    public List<AudienceSuspension> suspensions() {
        return List.copyOf(suspensions);
    }

    /** 아직 해제하지 않은 정지. 정지된 적이 없거나 모두 해제했으면 비어 있다. */
    public Optional<AudienceSuspension> currentSuspension() {
        return suspensions.isEmpty() ? Optional.empty()
                : Optional.of(suspensions.getLast()).filter(suspension -> !suspension.isLifted());
    }

    private static void requireMinimumAge(LocalDate birthDate, Instant now) {
        int ageOnSignupDate = AgeCalculator.ageOn(birthDate, KoreanTime.toDate(now));
        if (ageOnSignupDate < QuantityPolicy.MINIMUM_SIGNUP_AGE) {
            throw new RuleViolationException(AccountErrorCode.AUDIENCE_UNDER_MINIMUM_AGE);
        }
    }

    private static void requireAllRequiredConsents(SignupConsentChoices consentChoices) {
        Set<ConsentItem> missingItems = consentChoices.missingRequiredItems();
        if (!missingItems.isEmpty()) {
            throw new RuleViolationException(AccountErrorCode.SIGNUP_REQUIRED_CONSENT_MISSING,
                    "빠진 필수 동의: " + missingItems);
        }
    }

    private static void requirePhoneNumberAvailable(PhoneNumberAvailability phoneNumberAvailability) {
        if (phoneNumberAvailability != PhoneNumberAvailability.AVAILABLE) {
            throw new RuleViolationException(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED,
                    "전화번호 사용 불가: " + phoneNumberAvailability);
        }
    }

    private static void requireEmailAvailable(EmailAvailability emailAvailability) {
        if (emailAvailability != EmailAvailability.AVAILABLE) {
            throw new RuleViolationException(AccountErrorCode.AUDIENCE_EMAIL_ALREADY_REGISTERED);
        }
    }

    private static void requireVerificationMatchesStatus(AudienceAccountStatus status, Instant emailVerifiedAt) {
        boolean verified = emailVerifiedAt != null;
        boolean mismatch = switch (status) {
            case PENDING_VERIFICATION -> verified;
            case ACTIVE, SUSPENDED -> !verified;
            case WITHDRAWN -> false;
        };
        if (mismatch) {
            throw new IllegalArgumentException("상태(" + status + ")와 이메일 인증 여부(" + verified + ")가 맞지 않습니다");
        }
    }

    /**
     * 풀리지 않은 정지는 많아야 하나이고 이력의 마지막이어야 한다. 이용 정지 상태면 그런 정지가 있어야 하고, 인증 대기·활동이면
     * 없어야 한다. 탈퇴는 정지된 채 탈퇴했을 수 있어 둘 다 받는다.
     */
    private static void requireSuspensionsMatchStatus(AudienceAccountStatus status, List<AudienceSuspension> suspensions) {
        long unliftedCount = suspensions.stream().filter(suspension -> !suspension.isLifted()).count();
        boolean lastIsUnlifted = !suspensions.isEmpty() && !suspensions.getLast().isLifted();
        if (unliftedCount > 1 || (unliftedCount == 1 && !lastIsUnlifted)) {
            throw new IllegalArgumentException("풀리지 않은 정지는 이력의 마지막 하나뿐이어야 합니다");
        }
        boolean mismatch = switch (status) {
            case SUSPENDED -> !lastIsUnlifted;
            case PENDING_VERIFICATION, ACTIVE -> lastIsUnlifted;
            case WITHDRAWN -> false;
        };
        if (mismatch) {
            throw new IllegalArgumentException("상태(" + status + ")와 정지 이력이 맞지 않습니다");
        }
    }
}
