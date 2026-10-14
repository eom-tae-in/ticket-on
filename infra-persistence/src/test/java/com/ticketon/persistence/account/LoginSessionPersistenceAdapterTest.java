package com.ticketon.persistence.account;

import static com.ticketon.persistence.jooq.Tables.SPRING_SESSION;
import static com.ticketon.persistence.jooq.Tables.SPRING_SESSION_ATTRIBUTES;
import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL에 마이그레이션(V1 spring_session)을 올리고, Spring Session JDBC가 쓰는 모양 그대로 세션 줄을 넣어 끊어 본다.
 * Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("로그인 세션 끊기 — spring_session을 주체 이름으로 지운다")
class LoginSessionPersistenceAdapterTest {

    /** 세션 시각 칸은 1970-01-01부터의 밀리초다. 끊기 판단에 쓰이지 않으므로 고정값을 넣는다. */
    private static final long CREATED_AT_MILLIS = 1_773_482_400_000L;

    private static final int MAX_INACTIVE_SECONDS = 1_800;

    private static final AccountRef AUDIENCE_7 = AccountRef.of(AccountKind.AUDIENCE, 7);

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private LoginSessionPersistenceAdapter adapter;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @AfterAll
    static void close() {
        dsl.close();
    }

    @BeforeEach
    void clearSessions() {
        dsl.deleteFrom(SPRING_SESSION).execute();
        adapter = new LoginSessionPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("같은 관객의 세션 두 개를 끊으면 둘 다 사라지고, 다른 주체의 세션은 남는다")
    void terminatesOnlySessionsOfThatAccount() {
        String phoneSession = insertSession(AUDIENCE_7.principalName());
        String laptopSession = insertSession(AUDIENCE_7.principalName());
        insertSession("AUDIENCE:8");
        insertSession("STAFF:7");

        int terminated = adapter.terminateAllSessions(AUDIENCE_7);

        assertThat(terminated).isEqualTo(2);
        assertThat(remainingPrincipalNames()).containsExactlyInAnyOrder("AUDIENCE:8", "STAFF:7");
        assertThat(attributeCountOf(phoneSession)).isZero();
        assertThat(attributeCountOf(laptopSession)).isZero();
    }

    @Test
    @DisplayName("같은 id의 운영진 로그인을 끊어도 관객 계정 세션은 남는다 — 같은 이메일로 양쪽 가입 (docs/01)")
    void sameIdOfOtherKindIsUntouched() {
        insertSession("AUDIENCE:7");
        insertSession("STAFF:7");

        int terminated = adapter.terminateAllSessions(AccountRef.of(AccountKind.STAFF, 7));

        assertThat(terminated).isEqualTo(1);
        assertThat(remainingPrincipalNames()).containsExactly("AUDIENCE:7");
    }

    @Test
    @DisplayName("입구 3곳의 태블릿 3대가 같은 검표 계정으로 로그인해 있으면, 비밀번호 재발급으로 셋 다 끊긴다 (docs/01 \"검표 계정\")")
    void terminatesEveryTabletOfCheckinAccount() {
        AccountRef checkin = AccountRef.of(AccountKind.CHECKIN, 3);
        insertSession(checkin.principalName());
        insertSession(checkin.principalName());
        insertSession(checkin.principalName());

        int terminated = adapter.terminateAllSessions(checkin);

        assertThat(terminated).isEqualTo(3);
        assertThat(remainingPrincipalNames()).isEmpty();
    }

    @Test
    @DisplayName("로그인하지 않은 세션(주체 없음)은 건드리지 않는다")
    void anonymousSessionsAreUntouched() {
        insertSession(null);
        insertSession(AUDIENCE_7.principalName());

        adapter.terminateAllSessions(AUDIENCE_7);

        assertThat(dsl.fetchCount(SPRING_SESSION)).isEqualTo(1);
        assertThat(dsl.fetchCount(SPRING_SESSION, SPRING_SESSION.PRINCIPAL_NAME.isNull())).isEqualTo(1);
    }

    @Test
    @DisplayName("로그인된 세션이 없어도 오류 없이 0을 돌려준다")
    void noSessionIsNotAnError() {
        insertSession("AUDIENCE:8");

        assertThat(adapter.terminateAllSessions(AUDIENCE_7)).isZero();
        assertThat(remainingPrincipalNames()).containsExactly("AUDIENCE:8");
    }

    /** Spring Session JDBC가 만드는 모양대로 세션 한 줄과 보안 정보 속성 한 줄을 넣는다. */
    private String insertSession(String principalName) {
        String primaryId = UUID.randomUUID().toString();
        dsl.insertInto(SPRING_SESSION)
                .set(SPRING_SESSION.PRIMARY_ID, primaryId)
                .set(SPRING_SESSION.SESSION_ID, UUID.randomUUID().toString())
                .set(SPRING_SESSION.CREATION_TIME, CREATED_AT_MILLIS)
                .set(SPRING_SESSION.LAST_ACCESS_TIME, CREATED_AT_MILLIS)
                .set(SPRING_SESSION.MAX_INACTIVE_INTERVAL, MAX_INACTIVE_SECONDS)
                .set(SPRING_SESSION.EXPIRY_TIME, CREATED_AT_MILLIS + MAX_INACTIVE_SECONDS * 1_000L)
                .set(SPRING_SESSION.PRINCIPAL_NAME, principalName)
                .execute();
        dsl.insertInto(SPRING_SESSION_ATTRIBUTES)
                .set(SPRING_SESSION_ATTRIBUTES.SESSION_PRIMARY_ID, primaryId)
                .set(SPRING_SESSION_ATTRIBUTES.ATTRIBUTE_NAME, "SPRING_SECURITY_CONTEXT")
                .set(SPRING_SESSION_ATTRIBUTES.ATTRIBUTE_BYTES, new byte[] {1})
                .execute();
        return primaryId;
    }

    private List<String> remainingPrincipalNames() {
        return dsl.select(SPRING_SESSION.PRINCIPAL_NAME)
                .from(SPRING_SESSION)
                .where(SPRING_SESSION.PRINCIPAL_NAME.isNotNull())
                .fetch(SPRING_SESSION.PRINCIPAL_NAME);
    }

    private int attributeCountOf(String primaryId) {
        return dsl.fetchCount(SPRING_SESSION_ATTRIBUTES, SPRING_SESSION_ATTRIBUTES.SESSION_PRIMARY_ID.eq(primaryId));
    }
}
