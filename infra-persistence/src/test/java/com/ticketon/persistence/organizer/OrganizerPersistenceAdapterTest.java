package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerPublicProfile;
import com.ticketon.domain.organizer.OrganizerStatus;
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
 * 진짜 PostgreSQL에 마이그레이션(V8 주최자)을 올리고 새 주최자 저장, 다시 읽기, 승인 저장(심사 중일 때만)을 본다.
 * Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자 저장소 (진짜 PostgreSQL)")
class OrganizerPersistenceAdapterTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerPersistenceAdapter adapter;

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
        new OrganizerRows(dsl).clear();
        adapter = new OrganizerPersistenceAdapter(dsl);
    }

    @Test
    @DisplayName("심사 중인 새 주최자를 저장하면 id가 붙고, 표에는 이름과 '심사 중' 상태가 남는다")
    void addsOrganizerUnderReview() {
        Organizer saved = adapter.add(Organizer.underReview(OrganizerName.of(" 극단 열두달 ")));

        assertThat(saved.id()).isPresent();
        assertThat(saved.status()).isEqualTo(OrganizerStatus.UNDER_REVIEW);
        assertThat(saved.publicProfile()).isEqualTo(OrganizerPublicProfile.empty());
        var row = dsl.selectFrom(ORGANIZER).where(ORGANIZER.ID.eq(saved.id().getAsLong())).fetchSingle();
        assertThat(row.getName()).isEqualTo("극단 열두달");
        assertThat(row.getStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    @DisplayName("같은 이름의 주최자도 저장한다 — 겹치는 이름은 막지 않고 심사에서 사람이 본다")
    void sameNameIsAllowed() {
        long first = adapter.add(Organizer.underReview(OrganizerName.of("극단 열두달"))).id().getAsLong();
        long second = adapter.add(Organizer.underReview(OrganizerName.of("극단 열두달"))).id().getAsLong();

        assertThat(second).isNotEqualTo(first);
        assertThat(dsl.fetchCount(ORGANIZER)).isEqualTo(2);
    }

    @Test
    @DisplayName("저장한 주최자를 id로 다시 읽는다 — 공개 정보는 아직 칸이 없어 비어 있다")
    void findsById() {
        long id = adapter.add(Organizer.underReview(OrganizerName.of("극단 열두달"))).id().getAsLong();

        Organizer found = adapter.findById(id).orElseThrow();

        assertThat(found.id()).hasValue(id);
        assertThat(found.name()).isEqualTo(OrganizerName.of("극단 열두달"));
        assertThat(found.status()).isEqualTo(OrganizerStatus.UNDER_REVIEW);
        assertThat(found.publicProfile()).isEqualTo(OrganizerPublicProfile.empty());
        assertThat(adapter.findById(id + 1000)).isEmpty();
    }

    @Test
    @DisplayName("승인한 주최자를 저장하면 표의 상태가 '활동'이 된다")
    void savesApproval() {
        long id = adapter.add(Organizer.underReview(OrganizerName.of("극단 열두달"))).id().getAsLong();
        Organizer organizer = adapter.findById(id).orElseThrow();
        organizer.approve();

        adapter.saveApproval(organizer);

        assertThat(adapter.findById(id).orElseThrow().status()).isEqualTo(OrganizerStatus.ACTIVE);
    }

    @Test
    @DisplayName("읽은 뒤 표에서 이미 심사 중이 아니게 됐으면 충돌이고 덮어쓰지 않는다")
    void approvalOfNoLongerUnderReviewIsConflict() {
        long id = adapter.add(Organizer.underReview(OrganizerName.of("극단 열두달"))).id().getAsLong();
        Organizer organizer = adapter.findById(id).orElseThrow();
        organizer.approve();
        dsl.update(ORGANIZER).set(ORGANIZER.STATUS, OrganizerStatus.CLOSED.name()).where(ORGANIZER.ID.eq(id)).execute();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.saveApproval(organizer))
                .satisfies(conflict -> assertThat(conflict.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_NOT_UNDER_REVIEW));
        assertThat(adapter.findById(id).orElseThrow().status()).isEqualTo(OrganizerStatus.CLOSED);
    }

    @Test
    @DisplayName("승인하지 않은 주최자나 저장 전 주최자는 승인으로 저장하지 않는다")
    void requiresApprovedSavedOrganizer() {
        long id = adapter.add(Organizer.underReview(OrganizerName.of("극단 열두달"))).id().getAsLong();
        Organizer underReview = adapter.findById(id).orElseThrow();
        Organizer unsaved = Organizer.underReview(OrganizerName.of("늦여름"));
        unsaved.approve();

        assertThatIllegalArgumentException().isThrownBy(() -> adapter.saveApproval(underReview));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.saveApproval(unsaved));
        assertThat(adapter.findById(id).orElseThrow().status()).isEqualTo(OrganizerStatus.UNDER_REVIEW);
    }

    @Test
    @DisplayName("이미 저장된 주최자는 새 주최자로 저장하지 않는다")
    void rejectsSavedOrganizer() {
        Organizer saved = Organizer.restore(5, OrganizerName.of("극단 열두달"), OrganizerStatus.UNDER_REVIEW,
                OrganizerPublicProfile.empty());

        assertThatIllegalArgumentException().isThrownBy(() -> adapter.add(saved));
        assertThat(dsl.fetchCount(ORGANIZER)).isZero();
    }
}
