package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.STAFF_EMAIL_VERIFICATION_TOKEN;

import com.ticketon.application.account.port.out.StaffEmailVerificationTokenRepository;
import com.ticketon.domain.account.StaffEmailVerificationToken;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 운영진 로그인 이메일 인증 링크 저장소(staff_email_verification_token — V5) 어댑터.
 */
@Component
public class StaffEmailVerificationTokenPersistenceAdapter implements StaffEmailVerificationTokenRepository {

    private final DSLContext dsl;

    public StaffEmailVerificationTokenPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(StaffEmailVerificationToken token) {
        dsl.insertInto(STAFF_EMAIL_VERIFICATION_TOKEN)
                .set(STAFF_EMAIL_VERIFICATION_TOKEN.TOKEN, token.value())
                .set(STAFF_EMAIL_VERIFICATION_TOKEN.STAFF_ACCOUNT_ID, token.staffAccountId())
                .set(STAFF_EMAIL_VERIFICATION_TOKEN.ISSUED_AT, toDatabaseTime(token.issuedAt()))
                .set(STAFF_EMAIL_VERIFICATION_TOKEN.EXPIRES_AT, toDatabaseTime(token.expiresAt()))
                .execute();
    }

    @Override
    public Optional<StaffEmailVerificationToken> findByValue(String value) {
        return dsl.selectFrom(STAFF_EMAIL_VERIFICATION_TOKEN)
                .where(STAFF_EMAIL_VERIFICATION_TOKEN.TOKEN.eq(value))
                .fetchOptional()
                .map(token -> new StaffEmailVerificationToken(token.getToken(), token.getStaffAccountId(),
                        token.getIssuedAt().toInstant(), token.getExpiresAt().toInstant()));
    }

    @Override
    public int deleteAllOf(long staffAccountId) {
        return dsl.deleteFrom(STAFF_EMAIL_VERIFICATION_TOKEN)
                .where(STAFF_EMAIL_VERIFICATION_TOKEN.STAFF_ACCOUNT_ID.eq(staffAccountId))
                .execute();
    }

    private OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
