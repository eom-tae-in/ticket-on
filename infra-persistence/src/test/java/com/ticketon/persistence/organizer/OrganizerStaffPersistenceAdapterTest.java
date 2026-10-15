package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.STAFF_MEMBERSHIP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.LocalDateTime;
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
 * 진짜 PostgreSQL에 마이그레이션(V8 소속)을 올리고 승인으로 연 명단 저장, 한 주최자의 명단 되살리기, 초대 수락으로 운영자 줄 더하기,
 * 나가기·내보내기로 줄 끝내기를 본다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자 운영진 명단 저장소 (진짜 PostgreSQL)")
class OrganizerStaffPersistenceAdapterTest {

    private static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 17, 15, 0));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerRows rows;

    private OrganizerStaffPersistenceAdapter adapter;

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
    void clearRows() {
        rows = new OrganizerRows(dsl);
        rows.clear();
        adapter = new OrganizerStaffPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("승인으로 연 명단을 저장하면 신청한 사람의 소유자 소속 한 줄이 승인 일시부터 지금 소속으로 남는다")
    void savesOpenedStaff() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);

        adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT));

        var row = dsl.selectFrom(STAFF_MEMBERSHIP).where(STAFF_MEMBERSHIP.ORGANIZER_ID.eq(organizerId)).fetchSingle();
        assertThat(row.getStaffAccountId()).isEqualTo(junghoon);
        assertThat(row.getRole()).isEqualTo("OWNER");
        assertThat(row.getStartedAt().toInstant()).isEqualTo(APPROVED_AT);
        assertThat(row.getEndedAt()).isNull();
        OrganizerStaff found = adapter.findByOrganizerId(organizerId);
        assertThat(found.currentOwner()).hasValue(junghoon);
        assertThat(found.memberships()).containsExactly(
                new StaffMembership(organizerId, junghoon, StaffRole.OWNER, APPROVED_AT, null));
    }

    @Test
    @DisplayName("이미 소속 줄이 있는 주최자에는 명단을 다시 열지 않는다")
    void opensOnlyOnce() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT));

        assertThatIllegalArgumentException().isThrownBy(
                () -> adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT.plusSeconds(60))));
        assertThat(dsl.fetchCount(STAFF_MEMBERSHIP)).isEqualTo(1);
    }

    @Test
    @DisplayName("명단을 되살리면 끝난 소속(이력)까지 시작한 순서로 돌아온다 — 다른 주최자의 소속은 섞이지 않는다")
    void restoresMembershipsWithHistory() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        long anotherOrganizer = rows.organizer("늦여름", OrganizerStatus.ACTIVE);
        Instant handedOverAt = APPROVED_AT.plusSeconds(86_400);
        rows.membership(organizerId, junghoon, StaffRole.OWNER, APPROVED_AT, handedOverAt);
        rows.currentMembership(organizerId, dohyun, StaffRole.OWNER, handedOverAt);
        rows.currentMembership(organizerId, junghoon, StaffRole.OPERATOR, handedOverAt);
        rows.currentMembership(anotherOrganizer, junghoon, StaffRole.OWNER, APPROVED_AT);

        OrganizerStaff found = adapter.findByOrganizerId(organizerId);

        assertThat(found.currentOwner()).hasValue(dohyun);
        assertThat(found.roleOf(junghoon)).contains(StaffRole.OPERATOR);
        assertThat(found.memberships()).containsExactly(
                new StaffMembership(organizerId, junghoon, StaffRole.OWNER, APPROVED_AT, handedOverAt),
                new StaffMembership(organizerId, dohyun, StaffRole.OWNER, handedOverAt, null),
                new StaffMembership(organizerId, junghoon, StaffRole.OPERATOR, handedOverAt, null));
    }

    @Test
    @DisplayName("소속이 없는 주최자(심사 중 · 반려된 신청의 주최자)는 빈 명단이다")
    void organizerWithoutStaff() {
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.UNDER_REVIEW);

        OrganizerStaff found = adapter.findByOrganizerId(organizerId);

        assertThat(found.memberships()).isEmpty();
        assertThat(found.currentOwner()).isEmpty();
    }

    @Test
    @DisplayName("초대를 수락한 운영자 줄을 더하면 그 주최자의 지금 운영자로 되살아난다")
    void addsJoinedOperator() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT));
        Instant joinedAt = APPROVED_AT.plusSeconds(86_400);

        adapter.addJoinedOperator(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, null));

        OrganizerStaff found = adapter.findByOrganizerId(organizerId);
        assertThat(found.roleOf(dohyun)).contains(StaffRole.OPERATOR);
        assertThat(found.currentOwner()).hasValue(junghoon);
    }

    @Test
    @DisplayName("이미 지금 소속된 사람의 운영자 줄은 표의 유일 색인이 막아 409 ALREADY_ORGANIZER_STAFF다 — 동시 수락 경합의 진 쪽")
    void rejectsSecondActiveMembership() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT));
        Instant joinedAt = APPROVED_AT.plusSeconds(86_400);
        adapter.addJoinedOperator(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, null));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.addJoinedOperator(
                        new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt.plusSeconds(1), null)))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(OrganizerErrorCode.ALREADY_ORGANIZER_STAFF));
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.addJoinedOperator(
                        new StaffMembership(organizerId, junghoon, StaffRole.OPERATOR, joinedAt, null)))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(OrganizerErrorCode.ALREADY_ORGANIZER_STAFF));
        assertThat(dsl.fetchCount(STAFF_MEMBERSHIP)).isEqualTo(2);
    }

    @Test
    @DisplayName("소유자 줄이나 이미 끝난 줄은 운영자 줄로 더하지 않는다")
    void addsOnlyActiveOperator() {
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);

        assertThatIllegalArgumentException().isThrownBy(() -> adapter.addJoinedOperator(
                new StaffMembership(organizerId, dohyun, StaffRole.OWNER, APPROVED_AT, null)));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.addJoinedOperator(
                new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, APPROVED_AT, APPROVED_AT.plusSeconds(1))));
    }

    @Test
    @DisplayName("나가거나 내보내진 소속에 끝난 일시를 남기면 지금 소속에서 빠지고 이력으로 남는다 — 다시 들어오면 새 줄이다")
    void savesEndedMembershipAndKeepsHistory() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT));
        Instant joinedAt = APPROVED_AT.plusSeconds(86_400);
        Instant leftAt = joinedAt.plusSeconds(86_400);
        Instant rejoinedAt = leftAt.plusSeconds(86_400);
        adapter.addJoinedOperator(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, null));

        adapter.saveEnded(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, leftAt));
        adapter.addJoinedOperator(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, rejoinedAt, null));

        assertThat(adapter.findByOrganizerId(organizerId).memberships()).containsExactly(
                new StaffMembership(organizerId, junghoon, StaffRole.OWNER, APPROVED_AT, null),
                new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, leftAt),
                new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, rejoinedAt, null));
    }

    @Test
    @DisplayName("그 사이 이미 끝난 소속을 다시 끝내려 하면 409 MEMBERSHIP_ALREADY_ENDED이고 먼저 남긴 끝난 일시가 그대로다")
    void rejectsEndingTwice() {
        long junghoon = rows.staffAccount("junghoon@example.com");
        long dohyun = rows.staffAccount("dohyun@example.com");
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        adapter.addOpened(OrganizerStaff.startWithOwner(organizerId, junghoon, APPROVED_AT));
        Instant joinedAt = APPROVED_AT.plusSeconds(86_400);
        Instant leftAt = joinedAt.plusSeconds(60);
        adapter.addJoinedOperator(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, null));
        adapter.saveEnded(new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, leftAt));

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.saveEnded(
                        new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, leftAt.plusSeconds(1))))
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(OrganizerErrorCode.MEMBERSHIP_ALREADY_ENDED));
        assertThat(adapter.findByOrganizerId(organizerId).memberships()).contains(
                new StaffMembership(organizerId, dohyun, StaffRole.OPERATOR, joinedAt, leftAt));
    }
}
