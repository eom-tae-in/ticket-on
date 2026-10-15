package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.STAFF_INVITATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerPublicProfile;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffInvitation;
import com.ticketon.domain.organizer.StaffInvitationStatus;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
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
 * 진짜 PostgreSQL에 마이그레이션(V12 운영자 초대)을 올리고 초대 저장 · 찾기 · 다시 보내기 · 답 저장과, 같은 이메일로 거의 동시에 초대하거나 같은
 * 초대에 거의 동시에 답할 때 진 쪽이 막히는지 본다. Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(박정훈 소유자)이 이도현의 이메일로 초대한다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("운영자 초대 저장소 (진짜 PostgreSQL)")
class StaffInvitationPersistenceAdapterTest {

    private static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2019, 3, 4, 10, 0));

    /** 초대한 일시 — 2026-10-15 10:00 (한국 시각). 유효 기간(7일)은 2026-10-22 10:00에 끝난다. */
    private static final Instant INVITED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant EXPIRES_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 22, 10, 0));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerRows rows;

    private StaffInvitationPersistenceAdapter adapter;

    private long junghoon;
    private long dohyun;
    private long planner;
    private long twelveMonths;
    private OrganizerStaff twelveMonthsStaff;

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
    void storeOrganizer() {
        rows = new OrganizerRows(dsl);
        rows.clear();
        adapter = new StaffInvitationPersistenceAdapter(dsl);
        junghoon = rows.staffAccount("junghoon@example.com");
        dohyun = rows.staffAccount("dohyun@example.com");
        planner = rows.staffAccount("planner@example.com");
        twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        rows.currentMembership(twelveMonths, junghoon, StaffRole.OWNER, APPROVED_AT);
        twelveMonthsStaff = OrganizerStaff.startWithOwner(twelveMonths, junghoon, APPROVED_AT);
    }

    private StaffInvitation invitation(String email, Instant invitedAt) {
        return StaffInvitation.issue(twelveMonthsStaff, junghoon, EmailAddress.of(email), OptionalLong.empty(), invitedAt);
    }

    private Organizer twelveMonthsOrganizer() {
        return Organizer.restore(twelveMonths, OrganizerName.of("극단 열두달"), OrganizerStatus.ACTIVE,
                OrganizerPublicProfile.empty());
    }

    /** 제 연결 · 트랜잭션에서 저장소를 써서 일하고 커밋한다 — 두 트랜잭션의 잠금을 보려고 연결을 따로 연다. */
    private static <T> T inOwnTransaction(TransactionWork<T> work) {
        try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            return ownConnection.transactionResult(
                    transaction -> work.run(new StaffInvitationPersistenceAdapter(transaction.dsl())));
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {

        T run(StaffInvitationPersistenceAdapter transactionAdapter) throws Exception;
    }

    private static void assertConflict(ThrowingCallable action, OrganizerErrorCode expectedCode) {
        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    @Test
    @DisplayName("보낸 초대를 저장하면 id가 붙고, 적은 이메일 · 보낸 사람 · 보낸 일시 · 유효 기간 · 답을 기다림이 그대로 돌아온다")
    void savesIssuedInvitation() {
        StaffInvitation saved = adapter.add(invitation("DoHyun@Example.com", INVITED_AT));

        long id = saved.id().orElseThrow();
        assertThat(saved.organizerId()).isEqualTo(twelveMonths);
        assertThat(saved.inviteeEmail()).isEqualTo(EmailAddress.of("DoHyun@Example.com"));
        assertThat(saved.invitedByStaffAccountId()).isEqualTo(junghoon);
        assertThat(saved.invitedAt()).isEqualTo(INVITED_AT);
        assertThat(saved.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(saved.status()).isEqualTo(StaffInvitationStatus.PENDING);
        assertThat(dsl.select(STAFF_INVITATION.INVITEE_EMAIL_LOWERCASE).from(STAFF_INVITATION)
                .where(STAFF_INVITATION.ID.eq(id)).fetchSingle().value1()).isEqualTo("dohyun@example.com");
    }

    @Test
    @DisplayName("같은 주최자가 같은 이메일(대소문자만 달라도)로 기다리는 초대가 있으면 표가 막아 409 STAFF_INVITATION_ALREADY_PENDING이다 — 동시 초대 경합의 진 쪽")
    void rejectsSecondPendingInvitationForSameEmail() {
        adapter.add(invitation("dohyun@example.com", INVITED_AT));

        assertConflict(() -> adapter.add(invitation("DOHYUN@example.com", INVITED_AT.plusSeconds(1))),
                OrganizerErrorCode.STAFF_INVITATION_ALREADY_PENDING);
        assertThat(dsl.fetchCount(STAFF_INVITATION)).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 주최자는 같은 이메일로 초대하고, 답한 초대가 있으면 같은 주최자도 다시 초대한다")
    void allowsOtherOrganizerAndAfterAnswer() {
        long lateSummer = rows.organizer("늦여름", OrganizerStatus.ACTIVE);
        rows.currentMembership(lateSummer, dohyun, StaffRole.OWNER, APPROVED_AT);
        StaffInvitation declined = adapter.add(invitation("planner@example.com", INVITED_AT));
        declined.decline(planner, EmailAddress.of("planner@example.com"), INVITED_AT.plusSeconds(60));
        adapter.saveAnswer(declined);

        adapter.add(StaffInvitation.issue(OrganizerStaff.startWithOwner(lateSummer, dohyun, APPROVED_AT), dohyun,
                EmailAddress.of("planner@example.com"), OptionalLong.empty(), INVITED_AT));
        adapter.add(invitation("planner@example.com", INVITED_AT.plusSeconds(120)));

        assertThat(dsl.fetchCount(STAFF_INVITATION)).isEqualTo(3);
    }

    @Test
    @DisplayName("기다리는 초대는 대소문자를 가리지 않고 찾고, 유효 기간이 지났어도 찾는다 — 답한 초대와 다른 주최자의 초대는 찾지 않는다")
    void findsPendingInvitation() {
        long id = adapter.add(invitation("dohyun@example.com", INVITED_AT)).id().orElseThrow();
        StaffInvitation declined = adapter.add(invitation("planner@example.com", INVITED_AT));
        declined.decline(planner, EmailAddress.of("planner@example.com"), INVITED_AT.plusSeconds(60));
        adapter.saveAnswer(declined);

        assertThat(adapter.findPending(twelveMonths, EmailAddress.of("DOHYUN@EXAMPLE.COM")))
                .hasValueSatisfying(found -> assertThat(found.id()).hasValue(id));
        assertThat(adapter.findPending(twelveMonths, EmailAddress.of("planner@example.com"))).isEmpty();
        assertThat(adapter.findPending(twelveMonths + 1000, EmailAddress.of("dohyun@example.com"))).isEmpty();
        assertThat(adapter.findById(id)).isPresent();
        assertThat(adapter.findById(id + 1000)).isEmpty();
    }

    @Test
    @DisplayName("수락을 저장하면 상태 · 답한 일시 · 답한 사람이 남는다")
    void savesAcceptance() {
        StaffInvitation invitation = adapter.add(invitation("dohyun@example.com", INVITED_AT));
        Instant acceptedAt = INVITED_AT.plus(Duration.ofDays(1));
        invitation.accept(dohyun, EmailAddress.of("dohyun@example.com"), twelveMonthsOrganizer(), twelveMonthsStaff,
                acceptedAt);

        adapter.saveAnswer(invitation);

        StaffInvitation found = adapter.findById(invitation.id().orElseThrow()).orElseThrow();
        assertThat(found.status()).isEqualTo(StaffInvitationStatus.ACCEPTED);
        assertThat(found.answeredAt()).contains(acceptedAt);
        assertThat(found.answeredByStaffAccountId()).hasValue(dohyun);
    }

    @Test
    @DisplayName("같은 초대에 먼저 답이 저장됐으면 늦게 읽은 쪽의 답은 409 STAFF_INVITATION_ALREADY_ANSWERED이고 먼저 저장된 답이 그대로다 — 수락 경합의 진 쪽")
    void rejectsSecondAnswer() {
        long id = adapter.add(invitation("dohyun@example.com", INVITED_AT)).id().orElseThrow();
        StaffInvitation readFirst = adapter.findById(id).orElseThrow();
        StaffInvitation readSecond = adapter.findById(id).orElseThrow();
        readFirst.accept(dohyun, EmailAddress.of("dohyun@example.com"), twelveMonthsOrganizer(), twelveMonthsStaff,
                INVITED_AT.plusSeconds(60));
        readSecond.decline(dohyun, EmailAddress.of("dohyun@example.com"), INVITED_AT.plusSeconds(61));
        adapter.saveAnswer(readFirst);

        assertConflict(() -> adapter.saveAnswer(readSecond), OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED);
        assertThat(adapter.findById(id).orElseThrow().status()).isEqualTo(StaffInvitationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("잠그고 읽으면 다른 트랜잭션이 잠근 초대는 그쪽이 커밋할 때까지 기다렸다가 커밋된 답을 읽는다 — 늦은 수락은 늘 '이미 답함'을 본다")
    void lockedReadWaitsForCommittedAnswer() throws Exception {
        long id = adapter.add(invitation("dohyun@example.com", INVITED_AT)).id().orElseThrow();
        CountDownLatch firstAnswered = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<StaffInvitationStatus> first = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                StaffInvitation locked = transactionAdapter.findByIdForUpdate(id).orElseThrow();
                locked.accept(dohyun, EmailAddress.of("dohyun@example.com"), twelveMonthsOrganizer(), twelveMonthsStaff,
                        INVITED_AT.plusSeconds(60));
                transactionAdapter.saveAnswer(locked);
                firstAnswered.countDown();
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(300);
                return locked.status();
            }));
            assertThat(firstAnswered.await(10, TimeUnit.SECONDS)).isTrue();
            Future<StaffInvitationStatus> second = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                secondStarted.countDown();
                return transactionAdapter.findByIdForUpdate(id).orElseThrow().status();
            }));

            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(StaffInvitationStatus.ACCEPTED);
            assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(StaffInvitationStatus.ACCEPTED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("유효 기간이 지난 초대를 다시 보내면 같은 줄의 보낸 사람 · 보낸 일시 · 유효 기간이 바뀐다")
    void savesReinvitation() {
        StaffInvitation invitation = adapter.add(invitation("dohyun@example.com", INVITED_AT));
        Instant reinvitedAt = EXPIRES_AT.plus(Duration.ofDays(3));
        invitation.reinvite(twelveMonthsStaff, junghoon, OptionalLong.empty(), reinvitedAt);

        adapter.saveReinvitation(invitation);

        StaffInvitation found = adapter.findById(invitation.id().orElseThrow()).orElseThrow();
        assertThat(found.invitedAt()).isEqualTo(reinvitedAt);
        assertThat(found.expiresAt()).isEqualTo(reinvitedAt.plus(Duration.ofDays(7)));
        assertThat(found.status()).isEqualTo(StaffInvitationStatus.PENDING);
        assertThat(dsl.fetchCount(STAFF_INVITATION)).isEqualTo(1);
    }

    @Test
    @DisplayName("그 사이 다른 요청이 먼저 다시 보냈으면 409 STAFF_INVITATION_ALREADY_PENDING, 먼저 답했으면 409 STAFF_INVITATION_ALREADY_ANSWERED다")
    void rejectsReinvitationLosingRace() {
        long id = adapter.add(invitation("dohyun@example.com", INVITED_AT)).id().orElseThrow();
        Instant reinvitedAt = EXPIRES_AT.plusSeconds(60);
        StaffInvitation readFirst = adapter.findById(id).orElseThrow();
        StaffInvitation readSecond = adapter.findById(id).orElseThrow();
        readFirst.reinvite(twelveMonthsStaff, junghoon, OptionalLong.empty(), reinvitedAt);
        readSecond.reinvite(twelveMonthsStaff, junghoon, OptionalLong.empty(), reinvitedAt.plusSeconds(1));
        adapter.saveReinvitation(readFirst);

        assertConflict(() -> adapter.saveReinvitation(readSecond), OrganizerErrorCode.STAFF_INVITATION_ALREADY_PENDING);

        StaffInvitation answered = adapter.findById(id).orElseThrow();
        StaffInvitation staleReinvitation = adapter.findById(id).orElseThrow();
        answered.decline(dohyun, EmailAddress.of("dohyun@example.com"), reinvitedAt.plusSeconds(10));
        adapter.saveAnswer(answered);
        Instant afterSecondExpiry = staleReinvitation.expiresAt().plusSeconds(1);
        staleReinvitation.reinvite(twelveMonthsStaff, junghoon, OptionalLong.empty(), afterSecondExpiry);

        assertConflict(() -> adapter.saveReinvitation(staleReinvitation),
                OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED);
    }
}
