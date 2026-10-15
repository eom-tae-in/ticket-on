package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER;
import static com.ticketon.persistence.jooq.Tables.ORGANIZER_APPLICATION;
import static com.ticketon.persistence.jooq.Tables.ORGANIZER_APPLICATION_LINK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.ApplicantReviewState;
import com.ticketon.domain.organizer.BusinessDeclaration;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.BusinessRegistrationNumber;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationForm;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.OrganizerType;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.flywaydb.core.Flyway;
import org.jooq.CloseableDSLContext;
import org.jooq.exception.DataAccessException;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL에 마이그레이션(V8 주최자 신청서)을 올리고 신청서 저장·되살리기, 심사 중 신청 확인, 같은 사람의 동시 신청 경합,
 * 심사 결과(승인·반려) 저장과 두 관리자의 동시 심사 경합을 본다.
 * 신청서는 docs/15의 두 주최자 — 극단 "열두달"(비사업자 단체)과 "늦여름"(사업자) — 다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자 신청서 저장소 (진짜 PostgreSQL)")
class OrganizerApplicationPersistenceAdapterTest {

    private static final Instant SUBMITTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant REVIEWED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 17, 15, 0));

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerRows rows;

    private OrganizerApplicationPersistenceAdapter adapter;

    private long junghoon;

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
        adapter = new OrganizerApplicationPersistenceAdapter(dsl);
        junghoon = rows.staffAccount("junghoon@example.com");
    }

    @Nested
    @DisplayName("저장과 되살리기")
    class AddAndFind {

        @Test
        @DisplayName("비사업자 신청서를 저장하면 id가 붙고, 다시 읽으면 심사받는 주최자·신청한 사람·항목·링크 순서가 그대로다")
        void nonBusinessApplicationRoundTrip() {
            long organizerId = rows.organizer("극단 열두달", OrganizerStatus.UNDER_REVIEW);

            OrganizerApplication saved = adapter.add(OrganizerApplication.submit(organizerId, junghoon,
                    twelveMonthsForm(), SUBMITTED_AT));

            assertThat(saved.id()).isPresent();
            OrganizerApplication found = adapter.findById(saved.id().getAsLong()).orElseThrow();
            assertThat(found.organizerId()).isEqualTo(organizerId);
            assertThat(found.applicantStaffAccountId()).isEqualTo(junghoon);
            assertThat(found.status()).isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW);
            assertThat(found.form()).isEqualTo(twelveMonthsForm());
            assertThat(found.submittedAt()).isEqualTo(SUBMITTED_AT);
            assertThat(found.reviewedAt()).isEmpty();
            assertThat(found.rejectionReason()).isEmpty();
        }

        @Test
        @DisplayName("사업자 신청서는 사업자 정보 다섯 칸이 그대로 돌아오고, 사업자등록번호는 숫자 10자리로 남는다")
        void businessApplicationRoundTrip() {
            long organizerId = rows.organizer("늦여름", OrganizerStatus.UNDER_REVIEW);

            long id = adapter.add(OrganizerApplication.submit(organizerId, junghoon, lateSummerForm(), SUBMITTED_AT))
                    .id().getAsLong();

            assertThat(adapter.findById(id).orElseThrow().form()).isEqualTo(lateSummerForm());
            assertThat(dsl.select(ORGANIZER_APPLICATION.BUSINESS_REGISTRATION_NUMBER).from(ORGANIZER_APPLICATION)
                    .where(ORGANIZER_APPLICATION.ID.eq(id)).fetchSingle().value1()).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("링크가 없는 신청서는 빈 목록으로 돌아온다")
        void applicationWithoutLinks() {
            long organizerId = rows.organizer("극단 열두달", OrganizerStatus.UNDER_REVIEW);
            OrganizerApplicationForm withoutLinks = new OrganizerApplicationForm(OrganizerType.INDIVIDUAL,
                    OrganizerName.of("박정훈"), TelephoneNumber.of("010-2345-6789"), EmailAddress.of("junghoon@example.com"),
                    List.of(), "첫 낭독회를 계획합니다.", BusinessDeclaration.nonBusiness(), twelveMonthsAccount());

            long id = adapter.add(OrganizerApplication.submit(organizerId, junghoon, withoutLinks, SUBMITTED_AT))
                    .id().getAsLong();

            assertThat(adapter.findById(id).orElseThrow().form().websiteAndSnsLinks()).isEmpty();
            assertThat(dsl.fetchCount(ORGANIZER_APPLICATION_LINK)).isZero();
        }

        @Test
        @DisplayName("반려된 신청서는 처리 일시와 사유가 함께 돌아온다")
        void rejectedApplicationRoundTrip() {
            long id = submitTwelveMonths();
            reject(id, "연락이 닿지 않음");

            OrganizerApplication found = adapter.findById(id).orElseThrow();

            assertThat(found.status()).isEqualTo(OrganizerApplicationStatus.REJECTED);
            assertThat(found.reviewedAt()).contains(REVIEWED_AT);
            assertThat(found.rejectionReason()).contains(RejectionReason.of("연락이 닿지 않음"));
        }

        @Test
        @DisplayName("없는 신청서면 비어 있다")
        void unknownApplication() {
            assertThat(adapter.findById(999)).isEmpty();
        }
    }

    @Nested
    @DisplayName("심사 중인 신청이 있는지 — 한 사람에게 하나")
    class ReviewState {

        @Test
        @DisplayName("신청한 적이 없으면 없음, 내면 있음이다 — 다른 사람의 신청은 세지 않는다")
        void underReviewAfterSubmit() {
            long dohyun = rows.staffAccount("dohyun@example.com");
            assertThat(adapter.reviewStateOf(junghoon)).isEqualTo(ApplicantReviewState.NO_APPLICATION_UNDER_REVIEW);

            submitTwelveMonths();

            assertThat(adapter.reviewStateOf(junghoon)).isEqualTo(ApplicantReviewState.APPLICATION_UNDER_REVIEW);
            assertThat(adapter.reviewStateOf(dohyun)).isEqualTo(ApplicantReviewState.NO_APPLICATION_UNDER_REVIEW);
        }

        @Test
        @DisplayName("반려되면 다시 없음이 되고, 새 신청서를 저장할 수 있다 — 재신청은 제한이 없다 (docs/01)")
        void reapplyAfterRejection() {
            long rejected = submitTwelveMonths();
            reject(rejected, "정보가 부족함");

            assertThat(adapter.reviewStateOf(junghoon)).isEqualTo(ApplicantReviewState.NO_APPLICATION_UNDER_REVIEW);
            long reapplied = submitTwelveMonths();

            assertThat(reapplied).isNotEqualTo(rejected);
            assertThat(adapter.reviewStateOf(junghoon)).isEqualTo(ApplicantReviewState.APPLICATION_UNDER_REVIEW);
            assertThat(dsl.fetchCount(ORGANIZER_APPLICATION)).isEqualTo(2);
        }

        @Test
        @DisplayName("심사 중인 신청이 있는데 또 저장하면 충돌이다 — 표의 유일 색인이 막는다")
        void secondUnderReviewIsConflict() {
            submitTwelveMonths();
            long anotherOrganizer = rows.organizer("극단 열두달", OrganizerStatus.UNDER_REVIEW);

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> adapter.add(OrganizerApplication.submit(anotherOrganizer, junghoon,
                            twelveMonthsForm(), SUBMITTED_AT.plusSeconds(60))))
                    .satisfies(conflict -> assertThat(conflict.errorCode())
                            .isEqualTo(OrganizerErrorCode.APPLICATION_ALREADY_UNDER_REVIEW));
            assertThat(dsl.fetchCount(ORGANIZER_APPLICATION)).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 사람이 두 트랜잭션에서 동시에 내면 하나만 남고, 진 쪽은 충돌로 되돌려져 함께 만든 주최자도 남지 않는다")
        void concurrentSubmissionsLeaveOne() throws Exception {
            CyclicBarrier bothChecked = new CyclicBarrier(2);

            List<Object> outcomes = runConcurrently(
                    () -> submitInOwnTransaction(bothChecked),
                    () -> submitInOwnTransaction(bothChecked));

            assertThat(outcomes).filteredOn(Long.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(ConflictException.class::isInstance).singleElement()
                    .satisfies(conflict -> assertThat(((ConflictException) conflict).errorCode())
                            .isEqualTo(OrganizerErrorCode.APPLICATION_ALREADY_UNDER_REVIEW));
            assertThat(dsl.fetchCount(ORGANIZER_APPLICATION)).isEqualTo(1);
            assertThat(dsl.fetchCount(ORGANIZER)).isEqualTo(1);
        }

        /** 제 연결·트랜잭션에서 심사 중 신청이 없음을 확인하고, 상대도 확인을 마칠 때까지 기다렸다가 주최자와 신청서를 저장한다. */
        private long submitInOwnTransaction(CyclicBarrier bothChecked) {
            try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                    POSTGRES.getPassword())) {
                return ownConnection.transactionResult(transaction -> {
                    OrganizerApplicationPersistenceAdapter transactionAdapter =
                            new OrganizerApplicationPersistenceAdapter(transaction.dsl());
                    assertThat(transactionAdapter.reviewStateOf(junghoon))
                            .isEqualTo(ApplicantReviewState.NO_APPLICATION_UNDER_REVIEW);
                    bothChecked.await(10, TimeUnit.SECONDS);
                    Organizer organizer = new OrganizerPersistenceAdapter(transaction.dsl())
                            .add(Organizer.underReview(OrganizerName.of("극단 열두달")));
                    return transactionAdapter.add(OrganizerApplication.submit(organizer.id().getAsLong(), junghoon,
                            twelveMonthsForm(), SUBMITTED_AT)).id().getAsLong();
                });
            }
        }
    }

    @Nested
    @DisplayName("심사 결과 저장 — 표에서 아직 심사 중일 때만 바꾼다")
    class SaveReview {

        @Test
        @DisplayName("승인하면 승인 상태와 처리 일시가 남고 사유는 비어 있다")
        void savesApproval() {
            long id = submitTwelveMonths();
            OrganizerApplication application = adapter.findById(id).orElseThrow();
            application.approve(organizerOf(application), REVIEWED_AT);

            adapter.saveReview(application);

            OrganizerApplication found = adapter.findById(id).orElseThrow();
            assertThat(found.status()).isEqualTo(OrganizerApplicationStatus.APPROVED);
            assertThat(found.reviewedAt()).contains(REVIEWED_AT);
            assertThat(found.rejectionReason()).isEmpty();
            assertThat(adapter.reviewStateOf(junghoon)).isEqualTo(ApplicantReviewState.NO_APPLICATION_UNDER_REVIEW);
        }

        @Test
        @DisplayName("반려하면 반려 상태와 처리 일시·사유가 남는다 — 신청 항목은 그대로다")
        void savesRejection() {
            long id = submitTwelveMonths();
            OrganizerApplication application = adapter.findById(id).orElseThrow();
            application.reject(RejectionReason.of("정보가 부족함"), REVIEWED_AT);

            adapter.saveReview(application);

            OrganizerApplication found = adapter.findById(id).orElseThrow();
            assertThat(found.status()).isEqualTo(OrganizerApplicationStatus.REJECTED);
            assertThat(found.reviewedAt()).contains(REVIEWED_AT);
            assertThat(found.rejectionReason()).contains(RejectionReason.of("정보가 부족함"));
            assertThat(found.form()).isEqualTo(twelveMonthsForm());
        }

        @Test
        @DisplayName("읽은 뒤 다른 처리가 먼저 마쳤으면 충돌이고 표는 먼저 마친 그대로다")
        void alreadyReviewedIsConflict() {
            long id = submitTwelveMonths();
            OrganizerApplication application = adapter.findById(id).orElseThrow();
            reject(id, "연락이 닿지 않음");
            application.approve(organizerOf(application), REVIEWED_AT);

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> adapter.saveReview(application))
                    .satisfies(conflict -> assertThat(conflict.errorCode())
                            .isEqualTo(OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED));
            OrganizerApplication found = adapter.findById(id).orElseThrow();
            assertThat(found.status()).isEqualTo(OrganizerApplicationStatus.REJECTED);
            assertThat(found.rejectionReason()).contains(RejectionReason.of("연락이 닿지 않음"));
        }

        @Test
        @DisplayName("처리하지 않은 신청서나 저장 전 신청서는 저장하지 않는다")
        void requiresReviewedSavedApplication() {
            long id = submitTwelveMonths();
            OrganizerApplication underReview = adapter.findById(id).orElseThrow();
            OrganizerApplication unsaved = OrganizerApplication.submit(underReview.organizerId(), junghoon,
                    twelveMonthsForm(), SUBMITTED_AT);
            unsaved.reject(RejectionReason.of("정보가 부족함"), REVIEWED_AT);

            assertThatIllegalArgumentException().isThrownBy(() -> adapter.saveReview(underReview));
            assertThatIllegalArgumentException().isThrownBy(() -> adapter.saveReview(unsaved));
            assertThat(adapter.findById(id).orElseThrow().status()).isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW);
        }

        @Test
        @DisplayName("두 관리자가 두 트랜잭션에서 동시에 승인·반려하면 하나만 저장되고, 진 쪽은 충돌로 되돌려진다")
        void concurrentReviewsLeaveOne() throws Exception {
            long id = submitTwelveMonths();
            CyclicBarrier bothRead = new CyclicBarrier(2);

            List<Object> outcomes = runConcurrently(
                    () -> reviewInOwnTransaction(id, bothRead, true),
                    () -> reviewInOwnTransaction(id, bothRead, false));

            assertThat(outcomes).filteredOn(Long.class::isInstance).hasSize(1);
            assertThat(outcomes).filteredOn(ConflictException.class::isInstance).singleElement()
                    .satisfies(conflict -> assertThat(((ConflictException) conflict).errorCode())
                            .isEqualTo(OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED));
            OrganizerApplication found = adapter.findById(id).orElseThrow();
            assertThat(found.status()).isIn(OrganizerApplicationStatus.APPROVED, OrganizerApplicationStatus.REJECTED);
            assertThat(found.rejectionReason().isPresent())
                    .isEqualTo(found.status() == OrganizerApplicationStatus.REJECTED);
        }

        /** 제 연결·트랜잭션에서 심사 중인 신청서를 읽고, 상대도 읽을 때까지 기다렸다가 승인하거나 반려해 저장한다. */
        private long reviewInOwnTransaction(long applicationId, CyclicBarrier bothRead, boolean approve) {
            try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                    POSTGRES.getPassword())) {
                return ownConnection.transactionResult(transaction -> {
                    OrganizerApplicationPersistenceAdapter transactionAdapter =
                            new OrganizerApplicationPersistenceAdapter(transaction.dsl());
                    OrganizerApplication application = transactionAdapter.findById(applicationId).orElseThrow();
                    assertThat(application.status()).isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW);
                    bothRead.await(10, TimeUnit.SECONDS);
                    if (approve) {
                        application.approve(new OrganizerPersistenceAdapter(transaction.dsl())
                                .findById(application.organizerId()).orElseThrow(), REVIEWED_AT);
                    } else {
                        application.reject(RejectionReason.of("연락이 닿지 않음"), REVIEWED_AT);
                    }
                    transactionAdapter.saveReview(application);
                    return applicationId;
                });
            }
        }

        private Organizer organizerOf(OrganizerApplication application) {
            return new OrganizerPersistenceAdapter(dsl).findById(application.organizerId()).orElseThrow();
        }
    }

    @Nested
    @DisplayName("표의 약속")
    class TableConstraints {

        @Test
        @DisplayName("비사업자 신청서에 사업자 항목을 넣거나, 사업자 신청서에서 사업자 항목을 빼면 표가 받지 않는다")
        void businessInformationMatchesBusinessStatus() {
            long id = submitTwelveMonths();

            assertThatThrownBy(() -> dsl.update(ORGANIZER_APPLICATION)
                    .set(ORGANIZER_APPLICATION.BUSINESS_TRADE_NAME, "열두달 컴퍼니")
                    .where(ORGANIZER_APPLICATION.ID.eq(id))
                    .execute()).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> dsl.update(ORGANIZER_APPLICATION)
                    .set(ORGANIZER_APPLICATION.BUSINESS_STATUS, "BUSINESS")
                    .where(ORGANIZER_APPLICATION.ID.eq(id))
                    .execute()).isInstanceOf(DataAccessException.class);
        }

        @Test
        @DisplayName("반려인데 사유가 없으면 표가 받지 않는다")
        void rejectionNeedsReason() {
            long id = submitTwelveMonths();

            assertThatThrownBy(() -> dsl.update(ORGANIZER_APPLICATION)
                    .set(ORGANIZER_APPLICATION.STATUS, "REJECTED")
                    .set(ORGANIZER_APPLICATION.REVIEWED_AT, REVIEWED_AT.atOffset(ZoneOffset.UTC))
                    .where(ORGANIZER_APPLICATION.ID.eq(id))
                    .execute()).isInstanceOf(DataAccessException.class);
        }
    }

    /** 박정훈이 극단 열두달을 심사 중 주최자로 만들어 신청서를 낸다. */
    private long submitTwelveMonths() {
        long organizerId = rows.organizer("극단 열두달", OrganizerStatus.UNDER_REVIEW);
        return adapter.add(OrganizerApplication.submit(organizerId, junghoon, twelveMonthsForm(), SUBMITTED_AT))
                .id().getAsLong();
    }

    /** 관리자가 반려한 것처럼 표를 바로 바꾼다 — 읽은 뒤 다른 관리자가 먼저 처리한 상황을 만들 때도 쓴다. */
    private static void reject(long applicationId, String reason) {
        dsl.update(ORGANIZER_APPLICATION)
                .set(ORGANIZER_APPLICATION.STATUS, OrganizerApplicationStatus.REJECTED.name())
                .set(ORGANIZER_APPLICATION.REVIEWED_AT, REVIEWED_AT.atOffset(ZoneOffset.UTC))
                .set(ORGANIZER_APPLICATION.REJECTION_REASON, reason)
                .where(ORGANIZER_APPLICATION.ID.eq(applicationId))
                .execute();
    }

    private static OrganizerApplicationForm twelveMonthsForm() {
        return new OrganizerApplicationForm(
                OrganizerType.GROUP,
                OrganizerName.of("극단 열두달"),
                TelephoneNumber.of("010-2345-6789"),
                EmailAddress.of("twelve-months@example.com"),
                List.of(ExternalLink.of("https://www.instagram.com/twelve_months_theater"),
                        ExternalLink.of("https://blog.example/twelve-months")),
                "2019년부터 소극장 정기공연을 해마다 두 번 올립니다. 2025 가을 정기공연, 2025 낭독회.",
                BusinessDeclaration.nonBusiness(),
                twelveMonthsAccount());
    }

    private static SettlementAccount twelveMonthsAccount() {
        return new SettlementAccount("국민은행", "123456-78-901234", "박정훈");
    }

    private static OrganizerApplicationForm lateSummerForm() {
        return new OrganizerApplicationForm(
                OrganizerType.GROUP,
                OrganizerName.of("늦여름"),
                TelephoneNumber.of("02-123-4567"),
                EmailAddress.of("hello@late-summer.example"),
                List.of(ExternalLink.of("https://late-summer.example")),
                "인디 페스티벌 3회 개최. 2027년 5월 3일권 페스티벌 계획.",
                BusinessDeclaration.business(new BusinessInformation("늦여름 레코드", "최여름", "서울특별시 마포구 와우산로 12",
                        TelephoneNumber.of("02-123-4567"), BusinessRegistrationNumber.of("123-45-67890"))),
                new SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드"));
    }

    /** 두 작업을 동시에 돌리고, 각각의 결과(돌려준 값 또는 던진 예외)를 순서대로 모은다. */
    private static List<Object> runConcurrently(Callable<Long> first, Callable<Long> second) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Long>> futures = List.of(executor.submit(first), executor.submit(second));
            List<Object> outcomes = new ArrayList<>();
            for (Future<Long> future : futures) {
                try {
                    outcomes.add(future.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException exception) {
                    outcomes.add(exception.getCause());
                } catch (TimeoutException exception) {
                    throw new AssertionError("동시 작업이 30초 안에 끝나지 않았습니다", exception);
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }
}
