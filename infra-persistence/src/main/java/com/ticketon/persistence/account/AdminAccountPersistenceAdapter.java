package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.ADMIN_ACCOUNT;

import com.ticketon.application.account.port.out.AdminAccountRepository;
import com.ticketon.domain.account.AccountErrorCode;
import com.ticketon.domain.account.AdminAccount;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.EmailAvailability;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.persistence.jooq.tables.records.AdminAccountRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 관리자 계정 저장소(admin_account — V9) 어댑터.
 *
 * <h2>이메일 하나에 관리자 계정 하나 — 동시 발급 경합</h2>
 * 발급은 "쓸 수 있는지 확인 → 저장" 두 단계라, 같은 이메일로 거의 동시에 발급하면 둘 다 확인을 통과할 수 있다. 그래서 마지막 판정은 표의
 * 유일 색인(소문자 이메일이 하나)이 한다. 저장은 {@code INSERT … ON CONFLICT DO NOTHING}으로 하고, 한 줄도 들어가지 않으면 진 쪽이다 —
 * 겹칠 수 있는 칸이 이메일 하나라 이메일 문구의 {@link ConflictException}(409)이 된다. 먼저 들어온 쪽이 아직 커밋하지 않았으면
 * PostgreSQL이 그 결과를 기다렸다가 판정하므로 둘 다 들어가는 일은 없다.
 *
 * <h2>이메일 대소문자</h2>
 * email 칸은 적은 그대로 두고, 비교는 PostgreSQL lower()로 소문자로 바꾼 email_lowercase로 한다(관객 계정·운영진 로그인과 같다).
 */
@Component
public class AdminAccountPersistenceAdapter implements AdminAccountRepository {

    private final DSLContext dsl;

    public AdminAccountPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public EmailAvailability emailAvailability(EmailAddress email) {
        return dsl.fetchExists(ADMIN_ACCOUNT, ADMIN_ACCOUNT.EMAIL_LOWERCASE.eq(lowercase(email)))
                ? EmailAvailability.ALREADY_REGISTERED
                : EmailAvailability.AVAILABLE;
    }

    @Override
    public AdminAccount add(AdminAccount newAccount) {
        if (newAccount.id().isPresent()) {
            throw new IllegalArgumentException("이미 저장된 관리자 계정입니다: " + newAccount.id().getAsLong());
        }
        Optional<Long> insertedId = dsl.insertInto(ADMIN_ACCOUNT)
                .set(ADMIN_ACCOUNT.GRADE, newAccount.grade().name())
                .set(ADMIN_ACCOUNT.EMAIL, newAccount.email().value())
                .set(ADMIN_ACCOUNT.EMAIL_LOWERCASE, lowercase(newAccount.email()))
                .set(ADMIN_ACCOUNT.PASSWORD_HASH, newAccount.passwordHash().value())
                .set(ADMIN_ACCOUNT.ISSUED_AT, toDatabaseTime(newAccount.issuedAt()))
                .onConflictDoNothing()
                .returningResult(ADMIN_ACCOUNT.ID)
                .fetchOptional()
                .map(Record1::value1);
        long id = insertedId.orElseThrow(() -> new ConflictException(AccountErrorCode.ADMIN_EMAIL_ALREADY_REGISTERED,
                "동시 발급 경합 — 같은 이메일의 관리자 계정이 먼저 저장됨"));
        return findById(id).orElseThrow(() -> new IllegalStateException("방금 저장한 관리자 계정을 찾지 못했습니다: " + id));
    }

    @Override
    public Optional<AdminAccount> findById(long id) {
        return dsl.selectFrom(ADMIN_ACCOUNT)
                .where(ADMIN_ACCOUNT.ID.eq(id))
                .fetchOptional()
                .map(this::toAccount);
    }

    /** 유일 색인(소문자 이메일이 하나)이 있어 많아야 한 줄이다. */
    @Override
    public Optional<AdminAccount> findByEmail(EmailAddress email) {
        return dsl.selectFrom(ADMIN_ACCOUNT)
                .where(ADMIN_ACCOUNT.EMAIL_LOWERCASE.eq(lowercase(email)))
                .fetchOptional()
                .map(this::toAccount);
    }

    private AdminAccount toAccount(AdminAccountRecord account) {
        return AdminAccount.restore(account.getId(), new EmailAddress(account.getEmail()),
                new PasswordHash(account.getPasswordHash()), AdminGrade.valueOf(account.getGrade()),
                account.getIssuedAt().toInstant());
    }

    private static Field<String> lowercase(EmailAddress email) {
        return DSL.lower(DSL.val(email.value()));
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
