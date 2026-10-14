package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.SPRING_SESSION;

import com.ticketon.application.account.port.out.SessionTerminationPort;
import com.ticketon.domain.account.AccountRef;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

/**
 * 로그인 세션 저장소(spring_session 표, V1) 어댑터.
 * <p>
 * 세션은 Spring Session JDBC가 만들고 읽는다. 로그인한 세션의 principal_name 칸에는 주체 이름 "종류:id"가 들어 있으므로
 * (app-api의 로그인 처리), 그 칸으로 한 주체의 세션을 모두 찾아 지운다. 세션 속성(spring_session_attributes)은
 * 외래 키의 ON DELETE CASCADE로 함께 지워진다.
 */
@Component
public class LoginSessionPersistenceAdapter implements SessionTerminationPort {

    private final DSLContext dsl;

    public LoginSessionPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public int terminateAllSessions(AccountRef account) {
        return dsl.deleteFrom(SPRING_SESSION)
                .where(SPRING_SESSION.PRINCIPAL_NAME.eq(account.principalName()))
                .execute();
    }
}
