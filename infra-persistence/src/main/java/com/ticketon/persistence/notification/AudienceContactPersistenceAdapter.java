package com.ticketon.persistence.notification;

import static com.ticketon.persistence.jooq.Tables.AUDIENCE_ACCOUNT;

import com.ticketon.application.notification.port.out.AudienceContactPort;
import com.ticketon.domain.account.AudienceAccountStatus;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.account.PhoneNumber;
import com.ticketon.domain.notification.ContactPoint;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 관객 계정 연락처 조회 어댑터 — 관객 계정 표(audience_account — V2)에서 이메일·전화번호만 읽는다. 탈퇴한 계정은 찾지 않는다.
 */
@Component
public class AudienceContactPersistenceAdapter implements AudienceContactPort {

    private final DSLContext dsl;

    public AudienceContactPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<ContactPoint> findContact(long audienceAccountId) {
        return dsl.select(AUDIENCE_ACCOUNT.EMAIL, AUDIENCE_ACCOUNT.PHONE_NUMBER)
                .from(AUDIENCE_ACCOUNT)
                .where(AUDIENCE_ACCOUNT.ID.eq(audienceAccountId))
                .and(AUDIENCE_ACCOUNT.STATUS.ne(AudienceAccountStatus.WITHDRAWN.name()))
                .fetchOptional(contact -> ContactPoint.of(new EmailAddress(contact.value1()),
                        new PhoneNumber(contact.value2())));
    }
}
