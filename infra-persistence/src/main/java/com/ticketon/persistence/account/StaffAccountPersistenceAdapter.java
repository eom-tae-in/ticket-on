package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.NicknameAvailability;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffAccountStatus;
import com.ticketon.domain.account.StaffPersonalInformation;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.persistence.jooq.tables.records.StaffAccountRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 운영진 로그인 계정 저장소(staff_account — V5) 어댑터.
 *
 * <h2>이메일·닉네임 하나에 계정 하나 — 동시 가입·변경 경합</h2>
 * 가입과 닉네임 변경은 "쓸 수 있는지 확인 → 저장" 두 단계라, 같은 이메일이나 닉네임으로 거의 동시에 요청하면 둘 다 확인을 통과할 수 있다.
 * 그래서 마지막 판정은 표의 유일 색인(탈퇴하지 않은 계정 사이에서 소문자 이메일이 하나, 모든 계정 사이에서 닉네임이 하나)이 한다.
 * <ul>
 *   <li>가입은 {@code INSERT … ON CONFLICT DO NOTHING}으로 하고, 한 줄도 들어가지 않으면 진 쪽이다. 진 쪽은 겹친 칸을 다시 조회해
 *       이메일이면 이메일 문구, 닉네임이면 닉네임 문구의 {@link ConflictException}(409)이 된다(관객 계정 어댑터와 같은 방식).</li>
 *   <li>닉네임 변경은 UPDATE라 ON CONFLICT를 쓸 수 없다. 유일 색인에 막히면 PostgreSQL이 그 문장을 거부하는데(SQLSTATE 23505),
 *       바꾸는 칸이 닉네임 하나라 겹친 칸을 다시 볼 필요 없이 닉네임 문구의 충돌로 바꾼다. 트랜잭션은 그 뒤 되돌려진다.</li>
 * </ul>
 * 먼저 들어온 쪽이 아직 커밋하지 않았으면 PostgreSQL이 그 결과를 기다렸다가 판정하므로 둘 다 들어가는 일은 없다.
 *
 * <h2>이메일 대소문자</h2>
 * email 칸은 적은 그대로 두고, 비교는 PostgreSQL lower()로 소문자로 바꾼 email_lowercase로 한다(관객 계정과 같다).
 *
 * <h2>탈퇴</h2>
 * 탈퇴하면 줄은 남기고 이름 · 이메일 · 비밀번호 해시 · 이메일 인증 일시를 지운다(V13). 닉네임과 가입 일시는 남는다. 같은 계정의 탈퇴가 거의
 * 동시에 두 번 들어와도 {@code UPDATE … WHERE status = 'ACTIVE'}가 하나만 통과시킨다.
 */
@Component
public class StaffAccountPersistenceAdapter implements StaffAccountRepository {

    private final DSLContext dsl;

    public StaffAccountPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public EmailAvailability emailAvailability(EmailAddress email) {
        return isRegistered(email) ? EmailAvailability.ALREADY_REGISTERED : EmailAvailability.AVAILABLE;
    }

    @Override
    public NicknameAvailability nicknameAvailability(Nickname nickname) {
        return isTaken(nickname) ? NicknameAvailability.ALREADY_TAKEN : NicknameAvailability.AVAILABLE;
    }

    /** 닉네임 목록을 배열 하나로 넘겨({@code nickname = ANY(?)}) 몇 개든 한 번에 묻는다. */
    @Override
    public Set<Nickname> takenNicknamesAmong(Collection<Nickname> nicknames) {
        if (nicknames.isEmpty()) {
            return Set.of();
        }
        String[] values = nicknames.stream().map(Nickname::value).distinct().toArray(String[]::new);
        return dsl.select(STAFF_ACCOUNT.NICKNAME)
                .from(STAFF_ACCOUNT)
                .where(STAFF_ACCOUNT.NICKNAME.eq(DSL.any(values)))
                .fetch(Record1::value1)
                .stream()
                .map(Nickname::new)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public StaffAccount add(StaffAccount newAccount) {
        if (newAccount.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 계정입니다: " + newAccount.id().getAsLong());
        }
        StaffPersonalInformation personalInformation = newAccount.personalInformation();
        Optional<Long> insertedId = dsl.insertInto(STAFF_ACCOUNT)
                .set(STAFF_ACCOUNT.STATUS, newAccount.status().name())
                .set(STAFF_ACCOUNT.NAME, personalInformation.name())
                .set(STAFF_ACCOUNT.NICKNAME, newAccount.nickname().value())
                .set(STAFF_ACCOUNT.EMAIL, personalInformation.email().value())
                .set(STAFF_ACCOUNT.EMAIL_LOWERCASE, lowercase(personalInformation.email()))
                .set(STAFF_ACCOUNT.PASSWORD_HASH, newAccount.passwordHash().value())
                .set(STAFF_ACCOUNT.SIGNED_UP_AT, toDatabaseTime(newAccount.signedUpAt()))
                .set(STAFF_ACCOUNT.EMAIL_VERIFIED_AT, newAccount.emailVerifiedAt().map(this::toDatabaseTime).orElse(null))
                .onConflictDoNothing()
                .returningResult(STAFF_ACCOUNT.ID)
                .fetchOptional()
                .map(Record1::value1);
        long id = insertedId.orElseThrow(() -> lostSignupRace(newAccount));
        return findById(id).orElseThrow(() -> new IllegalStateException("방금 저장한 운영진 계정을 찾지 못했습니다: " + id));
    }

    @Override
    public Optional<StaffAccount> findById(long id) {
        return dsl.selectFrom(STAFF_ACCOUNT)
                .where(STAFF_ACCOUNT.ID.eq(id))
                .fetchOptional()
                .map(this::toAccount);
    }

    /** {@code SELECT … FOR UPDATE}로 그 계정 줄을 트랜잭션이 끝날 때까지 잠근다. */
    @Override
    public Optional<StaffAccount> findByIdForUpdate(long id) {
        return dsl.selectFrom(STAFF_ACCOUNT)
                .where(STAFF_ACCOUNT.ID.eq(id))
                .forUpdate()
                .fetchOptional()
                .map(this::toAccount);
    }

    /** 유일 색인(탈퇴하지 않은 계정 사이에서 소문자 이메일이 하나)이 있어 많아야 한 줄이다. */
    @Override
    public Optional<StaffAccount> findRegisteredByEmail(EmailAddress email) {
        return dsl.selectFrom(STAFF_ACCOUNT)
                .where(STAFF_ACCOUNT.EMAIL_LOWERCASE.eq(lowercase(email)).and(notWithdrawn()))
                .fetchOptional()
                .map(this::toAccount);
    }

    @Override
    public void saveEmailVerification(StaffAccount verifiedAccount) {
        long id = verifiedAccount.id().orElseThrow(() -> new IllegalArgumentException("저장하지 않은 계정입니다"));
        Instant emailVerifiedAt = verifiedAccount.emailVerifiedAt()
                .orElseThrow(() -> new IllegalArgumentException("이메일 인증을 마치지 않은 계정입니다: " + id));
        int updatedRows = dsl.update(STAFF_ACCOUNT)
                .set(STAFF_ACCOUNT.STATUS, verifiedAccount.status().name())
                .set(STAFF_ACCOUNT.EMAIL_VERIFIED_AT, toDatabaseTime(emailVerifiedAt))
                .where(STAFF_ACCOUNT.ID.eq(id))
                .and(STAFF_ACCOUNT.STATUS.eq(StaffAccountStatus.PENDING_VERIFICATION.name()))
                .execute();
        if (updatedRows == 0) {
            throw new ConflictException(AccountErrorCode.EMAIL_ALREADY_VERIFIED, "인증 대기가 아니게 된 운영진 계정: " + id);
        }
    }

    @Override
    public void saveNickname(StaffAccount changedAccount) {
        long id = changedAccount.id().orElseThrow(() -> new IllegalArgumentException("저장하지 않은 계정입니다"));
        int updatedRows = updateNickname(id, changedAccount.nickname());
        if (updatedRows == 0) {
            throw new ConflictException(AccountErrorCode.STAFF_NOT_ACTIVE, "활동 중이 아니게 된 운영진 계정: " + id);
        }
    }

    @Override
    public void savePassword(StaffAccount changedAccount) {
        long id = changedAccount.id().orElseThrow(() -> new IllegalArgumentException("저장하지 않은 계정입니다"));
        int updatedRows = dsl.update(STAFF_ACCOUNT)
                .set(STAFF_ACCOUNT.PASSWORD_HASH, changedAccount.passwordHash().value())
                .where(STAFF_ACCOUNT.ID.eq(id))
                .and(STAFF_ACCOUNT.STATUS.eq(StaffAccountStatus.ACTIVE.name()))
                .execute();
        if (updatedRows == 0) {
            throw new ConflictException(AccountErrorCode.STAFF_NOT_ACTIVE, "활동 중이 아니게 된 운영진 계정: " + id);
        }
    }

    @Override
    public void saveWithdrawal(StaffAccount withdrawnAccount) {
        long id = withdrawnAccount.id().orElseThrow(() -> new IllegalArgumentException("저장하지 않은 계정입니다"));
        Instant withdrawnAt = withdrawnAccount.withdrawnAt()
                .orElseThrow(() -> new IllegalArgumentException("탈퇴하지 않은 계정입니다: " + id));
        int updatedRows = dsl.update(STAFF_ACCOUNT)
                .set(STAFF_ACCOUNT.STATUS, StaffAccountStatus.WITHDRAWN.name())
                .set(STAFF_ACCOUNT.WITHDRAWN_AT, toDatabaseTime(withdrawnAt))
                .setNull(STAFF_ACCOUNT.NAME)
                .setNull(STAFF_ACCOUNT.EMAIL)
                .setNull(STAFF_ACCOUNT.EMAIL_LOWERCASE)
                .setNull(STAFF_ACCOUNT.PASSWORD_HASH)
                .setNull(STAFF_ACCOUNT.EMAIL_VERIFIED_AT)
                .where(STAFF_ACCOUNT.ID.eq(id))
                .and(STAFF_ACCOUNT.STATUS.eq(StaffAccountStatus.ACTIVE.name()))
                .execute();
        if (updatedRows == 0) {
            throw new ConflictException(AccountErrorCode.STAFF_WITHDRAWN, "탈퇴 경합 — 활동 중이 아니게 된 운영진 계정: " + id);
        }
    }

    /** 유일 색인에 막히면 닉네임 충돌로 바꾼다. 그 밖의 오류는 그대로 내보낸다. */
    private int updateNickname(long id, Nickname nickname) {
        try {
            return dsl.update(STAFF_ACCOUNT)
                    .set(STAFF_ACCOUNT.NICKNAME, nickname.value())
                    .where(STAFF_ACCOUNT.ID.eq(id))
                    .and(STAFF_ACCOUNT.STATUS.eq(StaffAccountStatus.ACTIVE.name()))
                    .execute();
        } catch (RuntimeException exception) {
            if (UniqueViolation.isCauseOf(exception)) {
                throw new ConflictException(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN,
                        "닉네임 변경 경합 — 다른 운영진이 먼저 저장함 (계정 " + id + ")");
            }
            throw exception;
        }
    }

    private boolean isRegistered(EmailAddress email) {
        return dsl.fetchExists(STAFF_ACCOUNT, STAFF_ACCOUNT.EMAIL_LOWERCASE.eq(lowercase(email)).and(notWithdrawn()));
    }

    private boolean isTaken(Nickname nickname) {
        return dsl.fetchExists(STAFF_ACCOUNT, STAFF_ACCOUNT.NICKNAME.eq(nickname.value()));
    }

    /** 유일 색인에 막혀 저장하지 못했을 때 — 겹친 칸을 다시 보고 그 칸의 문구로 알린다. 이메일을 먼저 본다(가입 규칙과 같은 순서). */
    private RuntimeException lostSignupRace(StaffAccount newAccount) {
        if (isRegistered(newAccount.personalInformation().email())) {
            return new ConflictException(AccountErrorCode.STAFF_EMAIL_ALREADY_REGISTERED, "동시 가입 경합 — 이메일이 먼저 저장됨");
        }
        if (isTaken(newAccount.nickname())) {
            return new ConflictException(AccountErrorCode.STAFF_NICKNAME_ALREADY_TAKEN, "동시 가입 경합 — 닉네임이 먼저 저장됨");
        }
        return new IllegalStateException("운영진 계정을 저장하지 못했는데 겹치는 이메일·닉네임이 없습니다");
    }

    private StaffAccount toAccount(StaffAccountRecord account) {
        if (StaffAccountStatus.valueOf(account.getStatus()) == StaffAccountStatus.WITHDRAWN) {
            return StaffAccount.restoreWithdrawn(account.getId(), new Nickname(account.getNickname()),
                    account.getSignedUpAt().toInstant(), account.getWithdrawnAt().toInstant());
        }
        StaffPersonalInformation personalInformation = new StaffPersonalInformation(account.getName(),
                new EmailAddress(account.getEmail()));
        Instant emailVerifiedAt = account.getEmailVerifiedAt() == null ? null : account.getEmailVerifiedAt().toInstant();
        return StaffAccount.restore(account.getId(), personalInformation, new Nickname(account.getNickname()),
                new PasswordHash(account.getPasswordHash()), account.getSignedUpAt().toInstant(),
                StaffAccountStatus.valueOf(account.getStatus()), emailVerifiedAt);
    }

    private static Condition notWithdrawn() {
        return STAFF_ACCOUNT.STATUS.ne(StaffAccountStatus.WITHDRAWN.name());
    }

    private static Field<String> lowercase(EmailAddress email) {
        return DSL.lower(DSL.val(email.value()));
    }

    private OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
