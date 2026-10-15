package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.AUDIENCE_BLOCKED_PHONE_NUMBER;
import static com.ticketon.persistence.jooq.Tables.AUDIENCE_CONSENT_RECORD;

import com.ticketon.application.account.port.out.AudienceAccountRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AudienceAccount;
import com.ticketon.domain.account.AudienceAccountStatus;
import com.ticketon.domain.account.AudiencePersonalInformation;
import com.ticketon.domain.account.ConsentItem;
import com.ticketon.domain.account.ConsentRecord;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.PhoneNumber;
import com.ticketon.domain.account.PhoneNumberAvailability;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.persistence.jooq.tables.records.AudienceAccountRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertValuesStep4;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 관객 계정 저장소(audience_account, audience_consent_record, audience_blocked_phone_number — V2) 어댑터.
 *
 * <h2>번호·이메일 하나에 계정 하나 — 동시 가입 경합</h2>
 * 가입은 "쓸 수 있는지 확인 → 저장" 두 단계라, 같은 번호로 거의 동시에 가입하면 둘 다 확인을 통과할 수 있다.
 * 그래서 마지막 판정은 표의 유일 색인(탈퇴하지 않은 계정 사이에서 번호·소문자 이메일이 하나)이 한다.
 * 저장은 {@code INSERT … ON CONFLICT DO NOTHING}으로 하고, 한 줄도 들어가지 않으면 진 쪽이다.
 * 예외로 받지 않는 이유: PostgreSQL은 문장이 실패하면 그 트랜잭션의 다음 조회를 받지 않아, 어느 칸이 겹쳤는지 다시 볼 수 없다.
 * 진 쪽은 겹친 칸을 다시 조회해 번호면 번호 문구, 이메일이면 이메일 문구의 {@link ConflictException}(409)이 된다.
 * 먼저 들어온 쪽이 아직 커밋하지 않았으면 PostgreSQL이 그 결과를 기다렸다가 판정하므로 둘 다 들어가는 일은 없다.
 *
 * <h2>이메일 대소문자</h2>
 * email 칸은 적은 그대로 두고, 비교는 PostgreSQL lower()로 소문자로 바꾼 email_lowercase로 한다. 저장할 때도 같은 lower()로 채워
 * 두 칸이 어긋나지 않는다(표의 CHECK도 같은 식이다). 가입할 때 겹치는지 볼 때와 로그인할 때 계정을 찾을 때 모두 이 칸을 본다.
 *
 * <h2>아직 저장하지 않는 것</h2>
 * 이용 정지 이력은 관객 이용 정지(#125)가 표와 함께 더한다. 그때까지는 정지 이력 없이 되살린다.
 */
@Component
public class AudienceAccountPersistenceAdapter implements AudienceAccountRepository {

    private static final String WITHDRAWN = AudienceAccountStatus.WITHDRAWN.name();

    private final DSLContext dsl;

    public AudienceAccountPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public PhoneNumberAvailability phoneNumberAvailability(PhoneNumber phoneNumber) {
        if (isRegistered(phoneNumber)) {
            return PhoneNumberAvailability.ALREADY_REGISTERED;
        }
        if (dsl.fetchExists(AUDIENCE_BLOCKED_PHONE_NUMBER,
                AUDIENCE_BLOCKED_PHONE_NUMBER.PHONE_NUMBER.eq(phoneNumber.digits()))) {
            return PhoneNumberAvailability.BLOCKED;
        }
        return PhoneNumberAvailability.AVAILABLE;
    }

    @Override
    public EmailAvailability emailAvailability(EmailAddress email) {
        return isRegistered(email) ? EmailAvailability.ALREADY_REGISTERED : EmailAvailability.AVAILABLE;
    }

    @Override
    public AudienceAccount add(AudienceAccount newAccount) {
        if (newAccount.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 계정입니다: " + newAccount.id().getAsLong());
        }
        AudiencePersonalInformation personalInformation = newAccount.personalInformation();
        Optional<Long> insertedId = dsl.insertInto(AUDIENCE_ACCOUNT)
                .set(AUDIENCE_ACCOUNT.STATUS, newAccount.status().name())
                .set(AUDIENCE_ACCOUNT.NAME, personalInformation.name())
                .set(AUDIENCE_ACCOUNT.BIRTH_DATE, personalInformation.birthDate())
                .set(AUDIENCE_ACCOUNT.EMAIL, personalInformation.email().value())
                .set(AUDIENCE_ACCOUNT.EMAIL_LOWERCASE, lowercase(personalInformation.email()))
                .set(AUDIENCE_ACCOUNT.PHONE_NUMBER, personalInformation.phoneNumber().digits())
                .set(AUDIENCE_ACCOUNT.PASSWORD_HASH, newAccount.passwordHash().map(PasswordHash::value).orElse(null))
                .set(AUDIENCE_ACCOUNT.SIGNED_UP_AT, toDatabaseTime(newAccount.signedUpAt()))
                .set(AUDIENCE_ACCOUNT.EMAIL_VERIFIED_AT, newAccount.emailVerifiedAt().map(this::toDatabaseTime).orElse(null))
                .onConflictDoNothing()
                .returningResult(AUDIENCE_ACCOUNT.ID)
                .fetchOptional()
                .map(Record1::value1);
        long id = insertedId.orElseThrow(() -> lostSignupRace(personalInformation));
        insertConsentRecords(id, newAccount.consentRecords());
        return findById(id).orElseThrow(() -> new IllegalStateException("방금 저장한 관객 계정을 찾지 못했습니다: " + id));
    }

    @Override
    public Optional<AudienceAccount> findById(long id) {
        return dsl.selectFrom(AUDIENCE_ACCOUNT)
                .where(AUDIENCE_ACCOUNT.ID.eq(id))
                .fetchOptional()
                .map(this::toAccount);
    }

    /** 유일 색인(탈퇴하지 않은 계정 사이에서 소문자 이메일이 하나)이 있어 많아야 한 줄이다. */
    @Override
    public Optional<AudienceAccount> findRegisteredByEmail(EmailAddress email) {
        return dsl.selectFrom(AUDIENCE_ACCOUNT)
                .where(AUDIENCE_ACCOUNT.EMAIL_LOWERCASE.eq(lowercase(email)).and(notWithdrawn()))
                .fetchOptional()
                .map(this::toAccount);
    }

    @Override
    public void saveEmailVerification(AudienceAccount verifiedAccount) {
        long id = verifiedAccount.id().orElseThrow(() -> new IllegalArgumentException("저장하지 않은 계정입니다"));
        Instant emailVerifiedAt = verifiedAccount.emailVerifiedAt()
                .orElseThrow(() -> new IllegalArgumentException("이메일 인증을 마치지 않은 계정입니다: " + id));
        int updatedRows = dsl.update(AUDIENCE_ACCOUNT)
                .set(AUDIENCE_ACCOUNT.STATUS, verifiedAccount.status().name())
                .set(AUDIENCE_ACCOUNT.EMAIL_VERIFIED_AT, toDatabaseTime(emailVerifiedAt))
                .where(AUDIENCE_ACCOUNT.ID.eq(id))
                .and(AUDIENCE_ACCOUNT.STATUS.eq(AudienceAccountStatus.PENDING_VERIFICATION.name()))
                .execute();
        if (updatedRows == 0) {
            throw new ConflictException(AccountErrorCode.EMAIL_ALREADY_VERIFIED, "인증 대기가 아니게 된 계정: " + id);
        }
    }

    @Override
    public void savePassword(AudienceAccount changedAccount) {
        long id = changedAccount.id().orElseThrow(() -> new IllegalArgumentException("저장하지 않은 계정입니다"));
        PasswordHash passwordHash = changedAccount.passwordHash()
                .orElseThrow(() -> new IllegalArgumentException("비밀번호를 정하지 않은 계정입니다: " + id));
        int updatedRows = dsl.update(AUDIENCE_ACCOUNT)
                .set(AUDIENCE_ACCOUNT.PASSWORD_HASH, passwordHash.value())
                .where(AUDIENCE_ACCOUNT.ID.eq(id))
                .and(AUDIENCE_ACCOUNT.STATUS.in(AudienceAccountStatus.ACTIVE.name(), AudienceAccountStatus.SUSPENDED.name()))
                .execute();
        if (updatedRows == 0) {
            throw new ConflictException(AccountErrorCode.AUDIENCE_NOT_ACTIVE, "비밀번호를 바꿀 수 없게 된 계정: " + id);
        }
    }

    private boolean isRegistered(PhoneNumber phoneNumber) {
        return dsl.fetchExists(AUDIENCE_ACCOUNT,
                AUDIENCE_ACCOUNT.PHONE_NUMBER.eq(phoneNumber.digits()).and(notWithdrawn()));
    }

    private boolean isRegistered(EmailAddress email) {
        return dsl.fetchExists(AUDIENCE_ACCOUNT,
                AUDIENCE_ACCOUNT.EMAIL_LOWERCASE.eq(lowercase(email)).and(notWithdrawn()));
    }

    /** 유일 색인에 막혀 저장하지 못했을 때 — 겹친 칸을 다시 보고 그 칸의 문구로 알린다. 번호를 먼저 본다(가입 규칙과 같은 순서). */
    private RuntimeException lostSignupRace(AudiencePersonalInformation personalInformation) {
        if (isRegistered(personalInformation.phoneNumber())) {
            return new ConflictException(AccountErrorCode.PHONE_NUMBER_ALREADY_REGISTERED, "동시 가입 경합 — 번호가 먼저 저장됨");
        }
        if (isRegistered(personalInformation.email())) {
            return new ConflictException(AccountErrorCode.AUDIENCE_EMAIL_ALREADY_REGISTERED, "동시 가입 경합 — 이메일이 먼저 저장됨");
        }
        return new IllegalStateException("관객 계정을 저장하지 못했는데 겹치는 번호·이메일이 없습니다");
    }

    private void insertConsentRecords(long accountId, List<ConsentRecord> consentRecords) {
        if (consentRecords.isEmpty()) {
            return;
        }
        InsertValuesStep4<?, Long, String, Boolean, OffsetDateTime> insert = dsl.insertInto(AUDIENCE_CONSENT_RECORD,
                AUDIENCE_CONSENT_RECORD.AUDIENCE_ACCOUNT_ID, AUDIENCE_CONSENT_RECORD.ITEM, AUDIENCE_CONSENT_RECORD.AGREED,
                AUDIENCE_CONSENT_RECORD.RECORDED_AT);
        for (ConsentRecord consentRecord : consentRecords) {
            insert = insert.values(accountId, consentRecord.item().name(), consentRecord.agreed(),
                    toDatabaseTime(consentRecord.recordedAt()));
        }
        insert.execute();
    }

    private AudienceAccount toAccount(AudienceAccountRecord account) {
        AudiencePersonalInformation personalInformation = new AudiencePersonalInformation(account.getName(),
                account.getBirthDate(), new EmailAddress(account.getEmail()), new PhoneNumber(account.getPhoneNumber()));
        Instant emailVerifiedAt = account.getEmailVerifiedAt() == null ? null : account.getEmailVerifiedAt().toInstant();
        PasswordHash passwordHash = account.getPasswordHash() == null ? null : new PasswordHash(account.getPasswordHash());
        return AudienceAccount.restore(account.getId(), personalInformation, passwordHash,
                account.getSignedUpAt().toInstant(), consentRecordsOf(account.getId()),
                AudienceAccountStatus.valueOf(account.getStatus()), emailVerifiedAt, List.of());
    }

    /** 일어난 순서대로 — 일시가 같으면 먼저 넣은 줄이 앞이다. */
    private List<ConsentRecord> consentRecordsOf(long accountId) {
        return dsl.selectFrom(AUDIENCE_CONSENT_RECORD)
                .where(AUDIENCE_CONSENT_RECORD.AUDIENCE_ACCOUNT_ID.eq(accountId))
                .orderBy(AUDIENCE_CONSENT_RECORD.RECORDED_AT, AUDIENCE_CONSENT_RECORD.ID)
                .fetch(consentRecord -> new ConsentRecord(ConsentItem.valueOf(consentRecord.getItem()),
                        consentRecord.getAgreed(), consentRecord.getRecordedAt().toInstant()));
    }

    private static Condition notWithdrawn() {
        return AUDIENCE_ACCOUNT.STATUS.ne(WITHDRAWN);
    }

    private static Field<String> lowercase(EmailAddress email) {
        return DSL.lower(DSL.val(email.value()));
    }

    private OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
