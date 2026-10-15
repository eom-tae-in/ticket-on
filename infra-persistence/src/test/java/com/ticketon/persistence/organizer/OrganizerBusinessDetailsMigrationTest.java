package com.ticketon.persistence.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketon.domain.organizer.BusinessDeclaration;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.BusinessRegistrationNumber;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * V18이 이미 승인된 주최자의 사업자 정보 · 정산 계좌를 승인된 신청서의 값으로 채우는지 진짜 PostgreSQL로 본다 — V17까지 올린 표에 주최자 ·
 * 신청서를 넣어 두고 V18을 올린다. 심사 중 · 반려된 주최자는 비워 둔다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(비사업자)과 늦여름(사업자)은 승인됐고, 극단 봄날은 심사 중, 극단 가을은 반려돼 심사 중으로 남았다(docs/15).
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("V18 — 승인된 주최자의 사업자 정보 · 정산 계좌를 신청서에서 옮긴다 (진짜 PostgreSQL)")
class OrganizerBusinessDetailsMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private static long twelveMonths;
    private static long lateSummer;
    private static long spring;
    private static long autumn;

    @BeforeAll
    static void migrateWithRowsBeforeV18() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .target("17")
                .load()
                .migrate();
        dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        long applicant = longValue("""
                INSERT INTO staff_account (status, name, nickname, email, email_lowercase, password_hash, signed_up_at,
                                           email_verified_at)
                VALUES ('ACTIVE', '박정훈', '조용한고래', 'junghoon@example.com', 'junghoon@example.com', 'test-hashed',
                        now(), now())
                RETURNING id""");
        twelveMonths = organizer("극단 열두달", "ACTIVE");
        lateSummer = organizer("늦여름", "ACTIVE");
        spring = organizer("극단 봄날", "UNDER_REVIEW");
        autumn = organizer("극단 가을", "UNDER_REVIEW");
        dsl.execute("""
                INSERT INTO organizer_application (organizer_id, applicant_staff_account_id, status, organizer_type, name,
                    contact_phone_number, email, event_history_or_plan, business_status, settlement_bank_name,
                    settlement_account_number, settlement_account_holder, submitted_at, reviewed_at)
                VALUES (?, ?, 'APPROVED', 'GROUP', '극단 열두달', '010-2345-6789', 'twelve@example.com', '정기공연',
                        'NON_BUSINESS', '국민은행', '123456-78-901234', '박정훈', now() - interval '3 days',
                        now() - interval '1 day')""", twelveMonths, applicant);
        dsl.execute("""
                INSERT INTO organizer_application (organizer_id, applicant_staff_account_id, status, organizer_type, name,
                    contact_phone_number, email, event_history_or_plan, business_status, business_trade_name,
                    business_representative_name, business_address, business_phone_number, business_registration_number,
                    settlement_bank_name, settlement_account_number, settlement_account_holder, submitted_at, reviewed_at)
                VALUES (?, ?, 'APPROVED', 'GROUP', '늦여름', '02-123-4567', 'hello@late-summer.example', '페스티벌',
                        'BUSINESS', '늦여름 레코드', '최여름', '서울특별시 마포구 와우산로 12', '02-123-4567', '1234567890',
                        '신한은행', '110-123-456789', '늦여름 레코드', now() - interval '3 days', now() - interval '1 day')""",
                lateSummer, applicant);
        dsl.execute("""
                INSERT INTO organizer_application (organizer_id, applicant_staff_account_id, status, organizer_type, name,
                    contact_phone_number, email, event_history_or_plan, business_status, settlement_bank_name,
                    settlement_account_number, settlement_account_holder, submitted_at, reviewed_at, rejection_reason)
                VALUES (?, ?, 'REJECTED', 'GROUP', '극단 가을', '010-1111-2222', 'autumn@example.com', '낭독회',
                        'NON_BUSINESS', '하나은행', '111-222222-33333', '김가을', now() - interval '5 days',
                        now() - interval '4 days', '연락이 닿지 않음')""", autumn, applicant);
        dsl.execute("""
                INSERT INTO organizer_application (organizer_id, applicant_staff_account_id, status, organizer_type, name,
                    contact_phone_number, email, event_history_or_plan, business_status, settlement_bank_name,
                    settlement_account_number, settlement_account_holder, submitted_at)
                VALUES (?, ?, 'UNDER_REVIEW', 'INDIVIDUAL', '극단 봄날', '010-3333-4444', 'spring@example.com', '첫 공연',
                        'NON_BUSINESS', '농협은행', '302-1234-5678-91', '이봄날', now())""", spring, applicant);

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
    }

    @AfterAll
    static void close() {
        dsl.close();
    }

    @Test
    @DisplayName("승인된 비사업자 주최자는 사업자 여부와 정산 계좌가 채워지고 사업자 정보 칸은 비어 있다")
    void fillsNonBusinessOrganizer() {
        assertThat(new OrganizerPersistenceAdapter(dsl).findBusinessDetails(twelveMonths)).contains(
                new OrganizerBusinessDetails(BusinessDeclaration.nonBusiness(),
                        new SettlementAccount("국민은행", "123456-78-901234", "박정훈")));
    }

    @Test
    @DisplayName("승인된 사업자 주최자는 사업자 정보 다섯 칸까지 채워진다")
    void fillsBusinessOrganizer() {
        assertThat(new OrganizerPersistenceAdapter(dsl).findBusinessDetails(lateSummer)).contains(
                new OrganizerBusinessDetails(
                        BusinessDeclaration.business(new BusinessInformation("늦여름 레코드", "최여름", "서울특별시 마포구 와우산로 12",
                                TelephoneNumber.of("02-123-4567"), BusinessRegistrationNumber.of("1234567890"))),
                        new SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드")));
    }

    @Test
    @DisplayName("심사 중인 주최자와 반려돼 심사 중으로 남은 주최자는 비워 둔다 — 신청서가 그 값을 갖는다")
    void leavesUnapprovedOrganizersEmpty() {
        OrganizerPersistenceAdapter adapter = new OrganizerPersistenceAdapter(dsl);

        assertThat(adapter.findBusinessDetails(spring)).isEmpty();
        assertThat(adapter.findBusinessDetails(autumn)).isEmpty();
        assertThat(dsl.fetchSingle("SELECT settlement_bank_name FROM organizer WHERE id = ?", autumn).get(0)).isNull();
    }

    private static long organizer(String name, String status) {
        return longValue("INSERT INTO organizer (name, status) VALUES (?, ?) RETURNING id", name, status);
    }

    /** 한 줄 한 칸을 돌려주는 SQL의 값 — 줄을 넣고 RETURNING id로 받은 id. */
    private static long longValue(String sql, Object... bindings) {
        return dsl.fetchSingle(sql, bindings).get(0, Long.class);
    }
}
