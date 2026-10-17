package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.DRAFT_EVENT_IDLE_NOTICE;
import static com.ticketon.persistence.jooq.Tables.EVENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.event.query.IdleDraftEvent;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventBasicInformation;
import com.ticketon.domain.event.EventCategoryCatalog;
import com.ticketon.domain.event.EventPublicAddress;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.EventType;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.persistence.organizer.OrganizerRows;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.exception.DataAccessException;
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
 * 진짜 PostgreSQL에 마이그레이션(V25 알림 기록)을 올리고 작성 중 행사 30일 미수정 알림 저장소를 본다 — 대상(활동 중인 주최자의 지우지 않은 작성 중
 * 행사)만 찾는지, 마지막 수정 시각의 구간마다 한 번만 남는지, CHECK가 어긋난 줄을 거부하는지. 극단 열두달(docs/15 박정훈)의 ○○밴드 단독공연을
 * 9/14 15:00에 마지막으로 고쳤고, 알림 작업은 30일 뒤인 10/14 15:00(상한 9/14 15:00)에 돈다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("작성 중 행사 30일 미수정 알림 저장소 (진짜 PostgreSQL)")
class DraftEventIdleNoticePersistenceAdapterTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final String CHECK_VIOLATION = "23514";

    private static final Instant LAST_MODIFIED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 9, 14, 15, 0));
    private static final Instant CUTOFF = LAST_MODIFIED_AT;
    private static final Instant NOTIFIED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 14, 15, 0));

    private static CloseableDSLContext dsl;

    private DraftEventIdleNoticePersistenceAdapter adapter;
    private EventPersistenceAdapter events;
    private OrganizerRows rows;
    private long twelveMonths;
    private int addressSequence;

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
        twelveMonths = rows.organizer("극단 열두달", OrganizerStatus.ACTIVE);
        events = new EventPersistenceAdapter(dsl);
        adapter = new DraftEventIdleNoticePersistenceAdapter(dsl);
        addressSequence = 0;
    }

    @Test
    @DisplayName("마지막 수정이 상한(30일 전) 이전인 작성 중 행사만 찾는다 — 상한과 같으면 들고, 1마이크로초 뒤(29일 23:59:59.999999)면 빠진다")
    void findsDraftsModifiedUpToCutoff() {
        long thirtyDays = addDraft(twelveMonths, "○○밴드 단독공연", LAST_MODIFIED_AT);
        long longAgo = addDraft(twelveMonths, "작년 겨울 기획", LAST_MODIFIED_AT.minus(Duration.ofDays(200)));
        addDraft(twelveMonths, "가을 낭독회", LAST_MODIFIED_AT.plus(KoreanTime.PRECISION));
        addDraft(twelveMonths, "새 행사", LAST_MODIFIED_AT.plus(Duration.ofDays(1)));

        assertThat(adapter.findNoticeCandidates(CUTOFF)).containsExactly(thirtyDays, longAgo);
    }

    @Test
    @DisplayName("판매를 시작했거나 지운 행사, 활동 중이 아닌 주최자(닫힘 · 영구 정지)의 행사는 찾지 않는다")
    void skipsNonTargets() {
        long draft = addDraft(twelveMonths, "○○밴드 단독공연", LAST_MODIFIED_AT);
        long started = addDraft(twelveMonths, "가을 정기공연", LAST_MODIFIED_AT);
        dsl.update(EVENT).set(EVENT.STATUS, "AWAITING_OPEN").where(EVENT.ID.eq(started)).execute();
        long deleted = addDraft(twelveMonths, "지운 행사", LAST_MODIFIED_AT);
        dsl.update(EVENT).set(EVENT.DELETED_AT, NOTIFIED_AT.atOffset(ZoneOffset.UTC)).where(EVENT.ID.eq(deleted)).execute();
        long closed = rows.organizer("극단 봄날", OrganizerStatus.CLOSED);
        long closedDraft = addDraft(closed, "봄날 정기공연", LAST_MODIFIED_AT);
        long banned = rows.organizer("영구 정지된 극단", OrganizerStatus.PERMANENTLY_SUSPENDED);
        long bannedDraft = addDraft(banned, "정지된 공연", LAST_MODIFIED_AT);

        assertThat(adapter.findNoticeCandidates(CUTOFF)).containsExactly(draft);
        assertThat(adapter.lockDraftEvent(started)).isEmpty();
        assertThat(adapter.lockDraftEvent(deleted)).isEmpty();
        assertThat(adapter.lockDraftEvent(closedDraft)).isEmpty();
        assertThat(adapter.lockDraftEvent(bannedDraft)).isEmpty();
    }

    @Test
    @DisplayName("그 마지막 수정 시각의 구간에 알린 행사는 다시 찾지 않고, 고쳐서 마지막 수정 시각이 바뀌면 다시 찾는다")
    void skipsAlreadyNotifiedPeriodOnly() {
        long band = addDraft(twelveMonths, "○○밴드 단독공연", LAST_MODIFIED_AT.minus(Duration.ofDays(40)));
        assertThat(adapter.recordNotice(band, LAST_MODIFIED_AT.minus(Duration.ofDays(40)), NOTIFIED_AT)).isTrue();
        assertThat(adapter.findNoticeCandidates(CUTOFF)).isEmpty();

        dsl.update(EVENT).set(EVENT.LAST_MODIFIED_AT, LAST_MODIFIED_AT.atOffset(ZoneOffset.UTC)).where(EVENT.ID.eq(band)).execute();

        assertThat(adapter.findNoticeCandidates(CUTOFF)).containsExactly(band);
    }

    @Test
    @DisplayName("다시 읽으면 주최자명 · 제목 · 진행 단계 · 마지막 수정 시각이 나온다")
    void locksAndReadsDraft() {
        long band = addDraft(twelveMonths, "○○밴드 단독공연", LAST_MODIFIED_AT);
        dsl.update(EVENT).set(EVENT.WIZARD_PROGRESS_STEP, EventWizardStep.PRICING.name()).where(EVENT.ID.eq(band)).execute();

        assertThat(adapter.lockDraftEvent(band))
                .contains(new IdleDraftEvent(band, twelveMonths, "극단 열두달", "○○밴드 단독공연", EventWizardStep.PRICING, LAST_MODIFIED_AT));
        assertThat(adapter.lockDraftEvent(band + 100)).isEmpty();
    }

    @Test
    @DisplayName("같은 구간의 기록은 한 번만 남는다 — 두 번째는 false이고 줄이 늘지 않는다. 다른 구간은 따로 남는다")
    void recordsOncePerIdlePeriod() {
        long band = addDraft(twelveMonths, "○○밴드 단독공연", LAST_MODIFIED_AT);

        assertThat(adapter.recordNotice(band, LAST_MODIFIED_AT, NOTIFIED_AT)).isTrue();
        assertThat(adapter.recordNotice(band, LAST_MODIFIED_AT, NOTIFIED_AT.plus(Duration.ofDays(1)))).isFalse();
        Instant editedAt = LAST_MODIFIED_AT.plus(Duration.ofDays(35));
        assertThat(adapter.recordNotice(band, editedAt, editedAt.plus(Duration.ofDays(30)))).isTrue();

        assertThat(dsl.fetch(DRAFT_EVENT_IDLE_NOTICE).map(notice -> notice.getLastModifiedAt().toInstant() + " " + notice.getNotifiedAt().toInstant()))
                .containsExactlyInAnyOrder(LAST_MODIFIED_AT + " " + NOTIFIED_AT, editedAt + " " + editedAt.plus(Duration.ofDays(30)));
    }

    @Test
    @DisplayName("알린 시각이 구간의 시작(마지막 수정 시각)보다 늦지 않은 줄은 draft_event_idle_notice_notified_after_idle_ck가 거부한다")
    void rejectsNoticeBeforeIdlePeriod() {
        long band = addDraft(twelveMonths, "○○밴드 단독공연", LAST_MODIFIED_AT);

        assertRejectedBy("draft_event_idle_notice_notified_after_idle_ck",
                () -> adapter.recordNotice(band, LAST_MODIFIED_AT, LAST_MODIFIED_AT));
        assertRejectedBy("draft_event_idle_notice_notified_after_idle_ck",
                () -> adapter.recordNotice(band, LAST_MODIFIED_AT, LAST_MODIFIED_AT.minus(Duration.ofDays(1))));
    }

    private long addDraft(long organizerId, String title, Instant lastModifiedAt) {
        addressSequence++;
        String address = "Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6f" + String.format("%02d", addressSequence);
        return events.add(Event.create(organizerId, new EventPublicAddress(address), information(title), lastModifiedAt)).id()
                .getAsLong();
    }

    private static EventBasicInformation information(String title) {
        return new EventBasicInformation(EventTitle.of(title), EventType.PERFORMANCE,
                EventCategoryCatalog.initial().select(EventType.PERFORMANCE, "연극", null),
                "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.", new Venue("○○아트홀", "서울특별시 종로구 대학로 12"), null, null,
                null);
    }

    private static void assertRejectedBy(String constraint, Runnable insert) {
        assertThatExceptionOfType(DataAccessException.class)
                .isThrownBy(insert::run)
                .satisfies(rejected -> {
                    assertThat(rejected.sqlState()).isEqualTo(CHECK_VIOLATION);
                    assertThat(rejected.getMessage()).contains("\"" + constraint + "\"");
                });
    }
}
