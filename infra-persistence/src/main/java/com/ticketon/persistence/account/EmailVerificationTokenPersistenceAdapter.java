package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_EMAIL_VERIFICATION_TOKEN;

import com.ticketon.application.account.port.out.EmailVerificationTokenRepository;
import com.ticketon.domain.account.EmailVerificationToken;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 이메일 인증 링크 저장소(audience_email_verification_token — V2) 어댑터.
 */
@Component
public class EmailVerificationTokenPersistenceAdapter implements EmailVerificationTokenRepository {

    private final DSLContext dsl;

    public EmailVerificationTokenPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void add(EmailVerificationToken token) {
        dsl.insertInto(AUDIENCE_EMAIL_VERIFICATION_TOKEN)
                .set(AUDIENCE_EMAIL_VERIFICATION_TOKEN.TOKEN, token.value())
                .set(AUDIENCE_EMAIL_VERIFICATION_TOKEN.AUDIENCE_ACCOUNT_ID, token.audienceAccountId())
                .set(AUDIENCE_EMAIL_VERIFICATION_TOKEN.ISSUED_AT, toDatabaseTime(token.issuedAt()))
                .set(AUDIENCE_EMAIL_VERIFICATION_TOKEN.EXPIRES_AT, toDatabaseTime(token.expiresAt()))
                .execute();
    }

    @Override
    public Optional<EmailVerificationToken> findByValue(String value) {
        return dsl.selectFrom(AUDIENCE_EMAIL_VERIFICATION_TOKEN)
                .where(AUDIENCE_EMAIL_VERIFICATION_TOKEN.TOKEN.eq(value))
                .fetchOptional()
                .map(token -> new EmailVerificationToken(token.getToken(), token.getAudienceAccountId(),
                        token.getIssuedAt().toInstant(), token.getExpiresAt().toInstant()));
    }

    private OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
