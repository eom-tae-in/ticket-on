package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.STAFF_ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.application.organizer.query.StaffOperationHistoryEntry;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationRecord;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffPermission;
import com.ticketon.domain.organizer.StaffRole;
import com.ticketon.persistence.account.StaffAccountPersistenceAdapter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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
 * 진짜 PostgreSQL에 마이그레이션 전체를 올리고, 한 주최자의 운영진 조작 기록을 최근 것부터 이어 보는지, 이름이 지금 운영진 로그인의 것인지 본다.
 * 극단 열두달에서 대표 박정훈(소유자)과 기획 단원(운영자)이 조작하고, 늦여름에서도 조작이 하나 있다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자 이력의 운영진 조작 기록 조회 (진짜 PostgreSQL)")
class StaffOperationHistoryQueryPersistenceAdapterTest {

    private static final Instant FIRST_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerRows rows;

    private StaffOperationRecordPersistenceAdapter recordAdapter;

    private StaffOperationHistoryQueryPersistenceAdapter queryAdapter;

    private long twelveMonths;

    private long junghoon;

    private long planner;

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
    void storeRecords() {
        rows = new OrganizerRows(dsl);
        rows.clear();
        recordAdapter = new StaffOperationRecordPersistenceAdapter(dsl);
        queryAdapter = new StaffOperationHistoryQueryPersistenceAdapter(dsl);

        junghoon = rows.staffAccount("junghoon@example.com");
        planner = rows.staffAccount("planner@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        renameStaff(planner, "김기획", "푸른바람");
        twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        long lateSummer = rows.organizer("늦여름", OrganizerStatus.ACTIVE);

        record(new StaffPermission(twelveMonths, junghoon, StaffRole.OWNER, StaffAction.RENAME_ORGANIZER), twelveMonths,
                "주최자명 변경 — 극단 열두달 → 열두달 레퍼토리", FIRST_AT);
        record(new StaffPermission(lateSummer, dohyun, StaffRole.OPERATOR, StaffAction.EDIT_PUBLIC_PROFILE), lateSummer,
                "주최자 로고 변경", FIRST_AT.plusSeconds(30));
        record(new StaffPermission(twelveMonths, planner, StaffRole.OPERATOR, StaffAction.EDIT_PUBLIC_PROFILE),
                twelveMonths, "주최자 소개 · SNS 링크 수정", FIRST_AT.plusSeconds(60));
        record(new StaffPermission(twelveMonths, junghoon, StaffRole.OWNER, StaffAction.RENAME_ORGANIZER), twelveMonths,
                "주최자명 변경 — 열두달 레퍼토리 → 극단 열두달", FIRST_AT.plusSeconds(120));
    }

    @Test
    @DisplayName("그 주최자의 기록만 최근 것부터 나오고, 누가 · 언제 · 그때의 역할 · 무엇을이 함께 나온다")
    void listsOrganizerRecordsNewestFirst() {
        List<StaffOperationHistoryEntry> history = queryAdapter.historyOf(twelveMonths, null, 10);

        assertThat(history).extracting(StaffOperationHistoryEntry::summary).containsExactly(
                "주최자명 변경 — 열두달 레퍼토리 → 극단 열두달",
                "주최자 소개 · SNS 링크 수정",
                "주최자명 변경 — 극단 열두달 → 열두달 레퍼토리");
        assertThat(history.get(1)).satisfies(entry -> {
            assertThat(entry.operatedAt()).isEqualTo(FIRST_AT.plusSeconds(60));
            assertThat(entry.staffAccountId()).isEqualTo(planner);
            assertThat(entry.staffNickname()).isEqualTo("푸른바람");
            assertThat(entry.staffName()).isEqualTo("김기획");
            assertThat(entry.staffRole()).isEqualTo(StaffRole.OPERATOR);
            assertThat(entry.action()).isEqualTo(StaffAction.EDIT_PUBLIC_PROFILE);
            assertThat(entry.targetKind()).isEqualTo(StaffOperationTargetKind.ORGANIZER);
            assertThat(entry.targetId()).isEqualTo(twelveMonths);
        });
    }

    @Test
    @DisplayName("닉네임을 바꾸면 지난 기록에도 새 닉네임이 나온다 — 기록은 그 사람을 따라간다")
    void followsNicknameChange() {
        renameStaff(planner, "김기획", "조용한고래");

        assertThat(queryAdapter.historyOf(twelveMonths, null, 10))
                .filteredOn(entry -> entry.staffAccountId() == planner)
                .extracting(StaffOperationHistoryEntry::staffNickname)
                .containsExactly("조용한고래");
    }

    @Test
    @DisplayName("운영진 로그인을 탈퇴해 이름을 파기한 사람의 기록은 이름 없이 닉네임으로 남는다")
    void withdrawnStaffKeepsNicknameWithoutName() {
        StaffAccountPersistenceAdapter accounts = new StaffAccountPersistenceAdapter(dsl);
        StaffAccount account = accounts.findById(planner).orElseThrow();
        account.withdraw(FIRST_AT.plusSeconds(3_600));
        accounts.saveWithdrawal(account);

        assertThat(queryAdapter.historyOf(twelveMonths, null, 10))
                .filteredOn(entry -> entry.staffAccountId() == planner)
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.staffNickname()).isEqualTo("푸른바람");
                    assertThat(entry.staffName()).isNull();
                });
    }

    @Test
    @DisplayName("많아야 정한 만큼 나오고, 마지막 줄의 id 앞으로 이어 본다")
    void continuesBeforeLastRecord() {
        List<StaffOperationHistoryEntry> firstPage = queryAdapter.historyOf(twelveMonths, null, 2);
        List<StaffOperationHistoryEntry> secondPage =
                queryAdapter.historyOf(twelveMonths, firstPage.getLast().recordId(), 2);

        assertThat(firstPage).extracting(StaffOperationHistoryEntry::operatedAt)
                .containsExactly(FIRST_AT.plusSeconds(120), FIRST_AT.plusSeconds(60));
        assertThat(secondPage).extracting(StaffOperationHistoryEntry::operatedAt).containsExactly(FIRST_AT);
    }

    @Test
    @DisplayName("기록이 없는 주최자는 빈 목록이다")
    void emptyForOrganizerWithoutRecords() {
        long newOrganizer = rows.organizer("새 극단", OrganizerStatus.ACTIVE);

        assertThat(queryAdapter.historyOf(newOrganizer, null, 10)).isEmpty();
    }

    @Test
    @DisplayName("많아야 몇 줄인지는 1 이상이다")
    void rejectsNonPositiveLimit() {
        assertThatIllegalArgumentException().isThrownBy(() -> queryAdapter.historyOf(twelveMonths, null, 0));
    }

    private void record(StaffPermission permission, long organizerId, String summary, Instant operatedAt) {
        recordAdapter.add(StaffOperationRecord.of(permission, StaffOperationTargetKind.ORGANIZER, organizerId, summary,
                operatedAt));
    }

    private static void renameStaff(long staffAccountId, String name, String nickname) {
        dsl.update(STAFF_ACCOUNT)
                .set(STAFF_ACCOUNT.NAME, name)
                .set(STAFF_ACCOUNT.NICKNAME, nickname)
                .where(STAFF_ACCOUNT.ID.eq(staffAccountId))
                .execute();
    }
}
