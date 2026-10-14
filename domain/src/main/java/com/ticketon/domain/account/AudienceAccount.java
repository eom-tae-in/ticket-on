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
 * 관객 계정 — 가입 정보 넷, 동의 기록, 상태, 이메일 인증 여부(docs/01 "수집 정보 — 사용자", "약관과 동의").
 * <p>
 * 새 계정은 {@link #signUp}으로만 만든다. 가입 규칙 셋을 여기서 지킨다.
 * <ul>
 *   <li>가입일(한국 날짜)의 만 나이가 14세 미만이면 막는다. 동의 항목 "만 14세 이상입니다"에 체크했어도 생년월일로 다시 본다
 *       — 생년월일 차단과 동의 항목을 함께 두는 것이 연령 허위 기재를 막는 합리적 장치다(docs/01, docs/99 A-3).</li>
 *   <li>필수 동의 셋 중 하나라도 빠지면 막는다. 마케팅 수신은 동의하지 않아도 된다.</li>
 *   <li>같은 번호로 가입한 계정이 있거나 차단된 번호면 막는다 — 번호 하나에 계정 하나.</li>
 * </ul>
 * 상태는 의도가 드러나는 메서드로만 바꾼다. 시각은 인자로 받는다.
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 계정은 id가 없다.
 */
public final class AudienceAccount {

    private final Long id;
    private final AudiencePersonalInformation personalInformation;
    private final Instant signedUpAt;
    private final List<ConsentRecord> consentRecords;
    private AudienceAccountStatus status;
    private Instant emailVerifiedAt;

    private AudienceAccount(Long id, AudiencePersonalInformation personalInformation, Instant signedUpAt,
            List<ConsentRecord> consentRecords, AudienceAccountStatus status, Instant emailVerifiedAt) {
        this.id = id;
        this.personalInformation = Objects.requireNonNull(personalInformation, "personalInformation");
        this.signedUpAt = Objects.requireNonNull(signedUpAt, "signedUpAt");
        this.consentRecords = new ArrayList<>(List.copyOf(consentRecords));
        this.status = Objects.requireNonNull(status, "status");
        this.emailVerifiedAt = emailVerifiedAt;
    }

    /**
     * 가입한다. 새 계정은 인증 대기 상태이고, 네 동의 항목의 동의 여부를 가입 일시로 기록한다.
     *
     * @param phoneNumberAvailability 가입 정보의 전화번호를 쓸 수 있는지 — 저장소가 확인한 값
     * @param now                     가입 일시. 만 나이는 이 순간의 한국 날짜로 센다.
     * @throws RuleViolationException   만 14세 미만({@link AccountErrorCode#AUDIENCE_UNDER_MINIMUM_AGE}),
     *                                  필수 동의 누락({@link AccountErrorCode#SIGNUP_REQUIRED_CONSENT_MISSING}),
     *                                  가입된 번호 또는 차단된 번호({@link AccountErrorCode#PHONE_NUMBER_ALREADY_REGISTERED})
     * @throws IllegalArgumentException 생년월일이 가입일보다 늦으면
     */
    public static AudienceAccount signUp(AudiencePersonalInformation personalInformation,
            SignupConsentChoices consentChoices, PhoneNumberAvailability phoneNumberAvailability, Instant now) {
        Objects.requireNonNull(personalInformation, "personalInformation");
        Objects.requireNonNull(consentChoices, "consentChoices");
        Objects.requireNonNull(phoneNumberAvailability, "phoneNumberAvailability");
        Objects.requireNonNull(now, "now");
        requireMinimumAge(personalInformation.birthDate(), now);
        requireAllRequiredConsents(consentChoices);
        requirePhoneNumberAvailable(phoneNumberAvailability);
        return new AudienceAccount(null, personalInformation, now, consentChoices.recordsAt(now),
                AudienceAccountStatus.PENDING_VERIFICATION, null);
    }

    /**
     * 저장소에서 읽은 값으로 계정을 되살린다. 가입 규칙은 다시 보지 않는다.
     *
     * @param consentRecords  일어난 순서대로의 동의 기록
     * @param emailVerifiedAt 이메일 인증 일시. 인증 전이면 null.
     * @throws IllegalArgumentException 상태와 이메일 인증 여부가 맞지 않으면(인증 대기인데 인증 일시가 있는 등)
     */
    public static AudienceAccount restore(long id, AudiencePersonalInformation personalInformation, Instant signedUpAt,
            List<ConsentRecord> consentRecords, AudienceAccountStatus status, Instant emailVerifiedAt) {
        if (id < 1) {
            throw new IllegalArgumentException("계정 id는 1 이상이어야 합니다: " + id);
        }
        Objects.requireNonNull(status, "status");
        requireVerificationMatchesStatus(status, emailVerifiedAt);
        return new AudienceAccount(id, personalInformation, signedUpAt, consentRecords, status, emailVerifiedAt);
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

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public AudiencePersonalInformation personalInformation() {
        return personalInformation;
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
}
