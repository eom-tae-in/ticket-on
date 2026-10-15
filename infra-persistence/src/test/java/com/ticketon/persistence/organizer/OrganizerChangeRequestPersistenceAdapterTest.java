package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ADMIN_ACCOUNT;
import static com.ticketon.persistence.jooq.Tables.ORGANIZER_CHANGE_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.BusinessDeclaration;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.BusinessRegistrationNumber;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.OrganizerChangeRequest;
import com.ticketon.domain.organizer.OrganizerChangeRequestKind;
import com.ticketon.domain.organizer.OrganizerChangeRequestStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.domain.organizer.RequestedOrganizerChange;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * 진짜 PostgreSQL에 마이그레이션(V18 변경 요청)을 올리고 요청 저장 · 되살리기(종류마다 새 값 칸), 종류마다 확인 중인 요청 하나(동시 요청 경합),
 * 승인·반려 저장과 확인 경합, 표의 CHECK를 본다. 늦여름(사업자)의 대표 최여름이 소유자다. Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("정산 계좌 · 사업자 정보 변경 요청 저장소 (진짜 PostgreSQL)")
class OrganizerChangeRequestPersistenceAdapterTest {

    private static final Instant REQUESTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final Instant REVIEWED_AT = REQUESTED_AT.plusSeconds(86_400);

    private static final BusinessInformation LATE_SUMMER_BUSINESS = new BusinessInformation("늦여름 레코드", "최여름",
            "서울특별시 마포구 와우산로 12", TelephoneNumber.of("02-123-4567"), BusinessRegistrationNumber.of("123-45-67890"));

    private static final OrganizerBusinessDetails LATE_SUMMER_DETAILS = new OrganizerBusinessDetails(
            BusinessDeclaration.business(LATE_SUMMER_BUSINESS), new SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드"));

    private static final SettlementAccount WOORI = new SettlementAccount("우리은행", "1002-345-678901", "최여름");

    private static final BusinessInformation MOVED_BUSINESS = new BusinessInformation("늦여름 레코드", "최여름",
            "서울특별시 마포구 독막로 7", TelephoneNumber.of("02-765-4321"), BusinessRegistrationNumber.of("123-45-67890"));

    private static final RejectionReason REASON = RejectionReason.of("예금주가 대표자 이름과 다릅니다");

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static CloseableDSLContext dsl;

    private OrganizerChangeRequestPersistenceAdapter adapter;

    private long organizerId;

    private long yeoreum;

    private long admin;

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
    void prepareRows() {
        OrganizerRows rows = new OrganizerRows(dsl);
        rows.clear();
        dsl.truncate(ADMIN_ACCOUNT).cascade().execute();
        yeoreum = rows.staffAccount("yeoreum@example.com");
        organizerId = rows.organizer("늦여름", OrganizerStatus.ACTIVE);
        admin = adminAccount("admin@ticket-on.example");
        adapter = new OrganizerChangeRequestPersistenceAdapter(dsl);
    }

    private OrganizerChangeRequest newAccountRequest() {
        return OrganizerChangeRequest.request(organizerId, yeoreum, LATE_SUMMER_DETAILS,
                new RequestedOrganizerChange.NewSettlementAccount(WOORI), false, REQUESTED_AT);
    }

    private OrganizerChangeRequest newBusinessRequest() {
        return OrganizerChangeRequest.request(organizerId, yeoreum, LATE_SUMMER_DETAILS,
                new RequestedOrganizerChange.NewBusinessInformation(MOVED_BUSINESS), false, REQUESTED_AT);
    }

    @Test
    @DisplayName("정산 계좌 변경 요청을 저장하면 id가 붙은 확인 중 요청이 그대로 되살아나고, 표에는 정산 계좌 세 칸만 채워진다")
    void addsAndFindsSettlementAccountRequest() {
        long id = adapter.add(newAccountRequest()).id().orElseThrow();

        OrganizerChangeRequest found = adapter.findById(id).orElseThrow();
        assertThat(found.organizerId()).isEqualTo(organizerId);
        assertThat(found.requesterStaffAccountId()).isEqualTo(yeoreum);
        assertThat(found.kind()).isEqualTo(OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT);
        assertThat(found.requestedChange()).isEqualTo(new RequestedOrganizerChange.NewSettlementAccount(WOORI));
        assertThat(found.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(found.status()).isEqualTo(OrganizerChangeRequestStatus.UNDER_REVIEW);
        assertThat(found.reviewedAt()).isEmpty();
        assertThat(found.reviewerAdminAccountId()).isEmpty();
        var row = dsl.selectFrom(ORGANIZER_CHANGE_REQUEST).where(ORGANIZER_CHANGE_REQUEST.ID.eq(id)).fetchSingle();
        assertThat(row.getSettlementAccountNumber()).isEqualTo("1002-345-678901");
        assertThat(row.getBusinessTradeName()).isNull();
        assertThat(row.getBusinessRegistrationNumber()).isNull();
        assertThat(adapter.findById(id + 1)).isEmpty();
    }

    @Test
    @DisplayName("사업자 정보 변경 요청은 사업자 정보 다섯 칸만 채우고 그대로 되살아난다 — 사업자등록번호는 숫자만 10자리다")
    void addsAndFindsBusinessInformationRequest() {
        long id = adapter.add(newBusinessRequest()).id().orElseThrow();

        OrganizerChangeRequest found = adapter.findById(id).orElseThrow();
        assertThat(found.kind()).isEqualTo(OrganizerChangeRequestKind.BUSINESS_INFORMATION);
        assertThat(found.requestedChange()).isEqualTo(new RequestedOrganizerChange.NewBusinessInformation(MOVED_BUSINESS));
        var row = dsl.selectFrom(ORGANIZER_CHANGE_REQUEST).where(ORGANIZER_CHANGE_REQUEST.ID.eq(id)).fetchSingle();
        assertThat(row.getBusinessRegistrationNumber()).isEqualTo("1234567890");
        assertThat(row.getSettlementBankName()).isNull();
    }

    @Test
    @DisplayName("확인 중인 요청은 그 주최자 · 그 종류로 본다 — 다른 종류와 처리한 요청은 세지 않고, 두 종류가 함께 확인 중일 수 있다")
    void tellsUnderReviewByKind() {
        assertThat(adapter.hasUnderReview(organizerId, OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT)).isFalse();
        long accountRequestId = adapter.add(newAccountRequest()).id().orElseThrow();

        assertThat(adapter.hasUnderReview(organizerId, OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT)).isTrue();
        assertThat(adapter.hasUnderReview(organizerId, OrganizerChangeRequestKind.BUSINESS_INFORMATION)).isFalse();
        long businessRequestId = adapter.add(newBusinessRequest()).id().orElseThrow();
        assertThat(adapter.findUnderReviewOf(organizerId)).extracting(request -> request.id().orElseThrow())
                .containsExactly(accountRequestId, businessRequestId);

        OrganizerChangeRequest accountRequest = adapter.findById(accountRequestId).orElseThrow();
        accountRequest.reject(REASON, admin, REVIEWED_AT);
        adapter.saveReview(accountRequest);
        assertThat(adapter.hasUnderReview(organizerId, OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT)).isFalse();
        assertThat(adapter.findUnderReviewOf(organizerId)).extracting(request -> request.id().orElseThrow())
                .containsExactly(businessRequestId);
        assertThat(adapter.findUnderReviewOf(organizerId + 1000)).isEmpty();
    }

    @Test
    @DisplayName("확인한 뒤 같은 종류의 다른 요청이 먼저 저장됐으면 409 ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW이고 한 줄만 남는다")
    void rejectsSecondRequestUnderReview() {
        adapter.add(newAccountRequest());

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.add(newAccountRequest()))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW));
        assertThat(dsl.fetchCount(ORGANIZER_CHANGE_REQUEST)).isEqualTo(1);
    }

    @Test
    @DisplayName("반려되면 같은 종류를 다시 요청할 수 있다")
    void allowsNewRequestAfterRejection() {
        OrganizerChangeRequest first = adapter.add(newAccountRequest());
        first.reject(REASON, admin, REVIEWED_AT);
        adapter.saveReview(first);

        OrganizerChangeRequest second = adapter.add(OrganizerChangeRequest.request(organizerId, yeoreum,
                LATE_SUMMER_DETAILS, new RequestedOrganizerChange.NewSettlementAccount(WOORI), false, REVIEWED_AT));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(dsl.fetchCount(ORGANIZER_CHANGE_REQUEST)).isEqualTo(2);
    }

    @Test
    @DisplayName("승인을 저장하면 상태 · 처리 일시 · 처리한 관리자가, 반려를 저장하면 사유까지 남는다")
    void savesReview() {
        OrganizerChangeRequest approved = adapter.add(newAccountRequest());
        long approvedId = approved.id().orElseThrow();
        adapter.saveReview(OrganizerChangeRequest.restore(approvedId, organizerId, yeoreum,
                new RequestedOrganizerChange.NewSettlementAccount(WOORI), REQUESTED_AT, OrganizerChangeRequestStatus.APPROVED,
                REVIEWED_AT, admin, null));
        OrganizerChangeRequest rejected = adapter.add(newBusinessRequest());
        rejected.reject(REASON, admin, REVIEWED_AT);
        adapter.saveReview(rejected);

        OrganizerChangeRequest foundApproved = adapter.findById(approvedId).orElseThrow();
        assertThat(foundApproved.status()).isEqualTo(OrganizerChangeRequestStatus.APPROVED);
        assertThat(foundApproved.reviewedAt()).contains(REVIEWED_AT);
        assertThat(foundApproved.reviewerAdminAccountId()).hasValue(admin);
        assertThat(foundApproved.rejectionReason()).isEmpty();
        OrganizerChangeRequest foundRejected = adapter.findById(rejected.id().orElseThrow()).orElseThrow();
        assertThat(foundRejected.status()).isEqualTo(OrganizerChangeRequestStatus.REJECTED);
        assertThat(foundRejected.rejectionReason()).contains(REASON);
    }

    @Test
    @DisplayName("그 사이 다른 관리자가 먼저 처리했으면 409 ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED이고 먼저 저장한 결과가 그대로다")
    void rejectsSecondReview() {
        long id = adapter.add(newAccountRequest()).id().orElseThrow();
        OrganizerChangeRequest first = adapter.findById(id).orElseThrow();
        OrganizerChangeRequest second = adapter.findById(id).orElseThrow();
        first.reject(REASON, admin, REVIEWED_AT);
        second.reject(RejectionReason.of("정보가 부족함"), admin, REVIEWED_AT.plusSeconds(60));
        adapter.saveReview(first);

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.saveReview(second))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED));
        assertThat(adapter.findById(id).orElseThrow().rejectionReason()).contains(REASON);
    }

    @Test
    @DisplayName("두 트랜잭션이 거의 동시에 승인·반려하면 늦은 쪽은 먼저 처리한 쪽의 커밋을 기다렸다가 409가 된다")
    void concurrentReviewWaitsAndConflicts() throws Exception {
        long id = adapter.add(newAccountRequest()).id().orElseThrow();
        CountDownLatch firstSaved = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> approval = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                transactionAdapter.saveReview(OrganizerChangeRequest.restore(id, organizerId, yeoreum,
                        new RequestedOrganizerChange.NewSettlementAccount(WOORI), REQUESTED_AT,
                        OrganizerChangeRequestStatus.APPROVED, REVIEWED_AT, admin, null));
                firstSaved.countDown();
                Thread.sleep(300);
                return true;
            }));
            assertThat(firstSaved.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> rejection = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                OrganizerChangeRequest request = transactionAdapter.findById(id).orElseThrow();
                request.reject(REASON, admin, REVIEWED_AT.plusSeconds(60));
                transactionAdapter.saveReview(request);
                return true;
            }));

            assertThat(approval.get(30, TimeUnit.SECONDS)).isTrue();
            assertThat(rejection).failsWithin(30, TimeUnit.SECONDS)
                    .withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(ConflictException.class);
            assertThat(adapter.findById(id).orElseThrow().status()).isEqualTo(OrganizerChangeRequestStatus.APPROVED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("거의 동시에 같은 종류를 두 번 요청하면 늦은 쪽은 먼저 넣은 쪽의 커밋을 기다렸다가 409가 된다")
    void concurrentRequestsWaitAndConflict() throws Exception {
        CountDownLatch firstAdded = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> first = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                long id = transactionAdapter.add(newAccountRequest()).id().orElseThrow();
                firstAdded.countDown();
                Thread.sleep(300);
                return id;
            }));
            assertThat(firstAdded.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Long> second = executor.submit(() -> inOwnTransaction(
                    transactionAdapter -> transactionAdapter.add(newAccountRequest()).id().orElseThrow()));

            long firstId = first.get(30, TimeUnit.SECONDS);
            assertThat(second).failsWithin(30, TimeUnit.SECONDS)
                    .withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(ConflictException.class);
            assertThat(dsl.select(ORGANIZER_CHANGE_REQUEST.ID).from(ORGANIZER_CHANGE_REQUEST).fetch(ORGANIZER_CHANGE_REQUEST.ID))
                    .containsExactly(firstId);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("종류와 맞지 않는 새 값 칸, 처리한 관리자 없이 처리한 줄은 표가 받지 않는다")
    void tableRejectsInconsistentRows() {
        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> dsl.insertInto(ORGANIZER_CHANGE_REQUEST)
                .set(ORGANIZER_CHANGE_REQUEST.ORGANIZER_ID, organizerId)
                .set(ORGANIZER_CHANGE_REQUEST.KIND, "SETTLEMENT_ACCOUNT")
                .set(ORGANIZER_CHANGE_REQUEST.REQUESTED_BY_STAFF_ACCOUNT_ID, yeoreum)
                .set(ORGANIZER_CHANGE_REQUEST.REQUESTED_AT, REQUESTED_AT.atOffset(ZoneOffset.UTC))
                .set(ORGANIZER_CHANGE_REQUEST.BUSINESS_TRADE_NAME, "늦여름 레코드")
                .set(ORGANIZER_CHANGE_REQUEST.STATUS, "UNDER_REVIEW")
                .execute());
        long id = adapter.add(newAccountRequest()).id().orElseThrow();
        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> dsl.update(ORGANIZER_CHANGE_REQUEST)
                .set(ORGANIZER_CHANGE_REQUEST.STATUS, "APPROVED")
                .set(ORGANIZER_CHANGE_REQUEST.REVIEWED_AT, REVIEWED_AT.atOffset(ZoneOffset.UTC))
                .where(ORGANIZER_CHANGE_REQUEST.ID.eq(id))
                .execute());
        assertThat(adapter.findById(id).orElseThrow().status()).isEqualTo(OrganizerChangeRequestStatus.UNDER_REVIEW);
    }

    /** 제 연결 · 트랜잭션에서 저장소를 써서 일하고 커밋한다 — 두 트랜잭션의 잠금을 보려고 연결을 따로 연다. */
    private static <T> T inOwnTransaction(TransactionWork<T> work) {
        try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            return ownConnection.transactionResult(
                    transaction -> work.run(new OrganizerChangeRequestPersistenceAdapter(transaction.dsl())));
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {

        T run(OrganizerChangeRequestPersistenceAdapter transactionAdapter) throws Exception;
    }

    private static long adminAccount(String email) {
        return dsl.insertInto(ADMIN_ACCOUNT)
                .set(ADMIN_ACCOUNT.GRADE, "ADMIN")
                .set(ADMIN_ACCOUNT.EMAIL, email)
                .set(ADMIN_ACCOUNT.EMAIL_LOWERCASE, email.toLowerCase())
                .set(ADMIN_ACCOUNT.PASSWORD_HASH, "{test}hashed-password")
                .set(ADMIN_ACCOUNT.ISSUED_AT, REQUESTED_AT.minusSeconds(86_400).atOffset(ZoneOffset.UTC))
                .returningResult(ADMIN_ACCOUNT.ID)
                .fetchSingle()
                .value1();
    }
}
