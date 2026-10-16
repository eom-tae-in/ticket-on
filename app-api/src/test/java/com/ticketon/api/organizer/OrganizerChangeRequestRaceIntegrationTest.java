package com.ticketon.api.organizer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.admin.port.in.ApproveOrganizerChangeRequestCommand;
import com.ticketon.application.admin.port.in.ApproveOrganizerChangeRequestUseCase;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.RenameOrganizerCommand;
import com.ticketon.application.organizer.port.in.RenameOrganizerUseCase;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeCommand;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeUseCase;
import com.ticketon.application.organizer.port.in.TransferOwnershipCommand;
import com.ticketon.application.organizer.port.in.TransferOwnershipUseCase;
import com.ticketon.application.organizer.port.out.OrganizerRepository;
import com.ticketon.application.organizer.service.StaffAccountLocks;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.ErrorCode;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.RequestedOrganizerChange;
import com.ticketon.domain.organizer.SettlementAccount;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL로 정산 계좌 변경 요청 · 관리자의 변경 승인이 소유자 넘기기(운영진 계정 줄을 먼저 잠근다) · 주최자명 변경(주최자 줄을 잠근다)과
 * 가장 나쁜 순간에 엇갈려도 교착(40P01) 없이 잠금 순서(계정 줄 → 주최자 줄 → 요청 줄, {@link StaffAccountLocks})대로 차례를 기다리고, 기다린 뒤
 * 바뀐 소속을 보고 막히는 것을 고정한다(#31). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 붙잡을 흐름의 첫 잠금만 한 트랜잭션에서 걸어 둔 채 다른 스레드에서 맞서는 흐름을 부르고, 맞서는 흐름이 잠금을 기다리기 시작한 것을 PostgreSQL의
 * 잠금 표(pg_locks)로 확인한다. 그 순간 맞서는 흐름이 쥐지 않았어야 할 줄을 NOWAIT로 잡아 보고, 같은 트랜잭션에서 붙잡을 흐름을 끝까지 돌려
 * 커밋한다.
 * <p>
 * 늦여름(docs/15)의 소유자 최여름, 운영자 이도현. 사업자이고 정산 계좌는 신한은행이다. 운영진 로그인 · 주최자 · 소속 · 관리자 계정은 표에 바로
 * 넣는다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("정산 계좌 변경 요청 · 승인의 잠금 순서 — 가장 나쁜 순간에 엇갈려도 교착 없이 차례로 (진짜 PostgreSQL)")
class OrganizerChangeRequestRaceIntegrationTest {

    private static final SettlementAccount WOORI = new SettlementAccount("우리은행", "1002-345-678901", "최여름");

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private OrganizerRepository organizerRepository;

    @Autowired
    private RequestOrganizerChangeUseCase requestOrganizerChangeUseCase;

    @Autowired
    private ApproveOrganizerChangeRequestUseCase approveOrganizerChangeRequestUseCase;

    @Autowired
    private TransferOwnershipUseCase transferOwnershipUseCase;

    @Autowired
    private RenameOrganizerUseCase renameOrganizerUseCase;

    private String suffix;
    private long lateSummer;
    private long yeoreum;
    private long dohyun;
    private AccountRef admin;

    @BeforeEach
    void storeLateSummer() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        yeoreum = storeStaff("최여름", "여름" + suffix, "yeoreum-" + suffix + "@example.com");
        dohyun = storeStaff("이도현", "기타" + suffix, "dohyun-" + suffix + "@example.com");
        lateSummer = storeBusinessOrganizer("늦여름 " + suffix);
        storeMembership(lateSummer, yeoreum, "OWNER");
        storeMembership(lateSummer, dohyun, "OPERATOR");
        admin = AccountRef.of(AccountKind.ADMIN, storeAdmin("admin-" + suffix + "@ticket-on.example"));
    }

    @Test
    @DisplayName("소유자 넘기기가 계정 줄을 잠근 순간 들어온 정산 계좌 변경 요청은 주최자 줄을 잡기 전에 계정 줄에서 기다리고, 넘긴 뒤 다시 확인해 403 OWNER_ONLY_ACTION으로 멈춘다 — 넘긴 사람의 요청이 남지 않는다")
    void requestWaitsOnAccountRowWhileTransferHoldsItAndStops() throws Exception {
        Future<?> request = runInterleaved(
                () -> StaffAccountLocks.lockInIdOrder(staffAccountRepository, yeoreum, dohyun),
                () -> transferOwnershipUseCase.transfer(new TransferOwnershipCommand(acting(yeoreum), dohyun)),
                this::requestAccountChange,
                this::assertOrganizerRowFreeForForeignKeyChecks);

        assertFailedWith(request, PermissionDeniedException.class, OrganizerErrorCode.OWNER_ONLY_ACTION);
        assertThat(currentOwner()).isEqualTo(dohyun);
        assertThat(changeRequestCount()).isZero();
        assertThat(storedAccountNumber()).isEqualTo("110-123-456789");
    }

    @Test
    @DisplayName("소유자 넘기기가 계정 줄을 잠근 순간 들어온 변경 승인은 요청한 사람의 계정 줄에서 기다리고(주최자 줄 · 요청 줄은 아직 잡지 않음), 넘긴 뒤의 명단을 보고 409 CHANGE_REQUESTER_NOT_OWNER로 멈춘다 — 계좌는 그대로다")
    void approvalWaitsOnRequesterAccountRowWhileTransferHoldsItAndStops() throws Exception {
        long requestId = requestAccountChange();

        Future<?> approval = runInterleaved(
                () -> StaffAccountLocks.lockInIdOrder(staffAccountRepository, yeoreum, dohyun),
                () -> transferOwnershipUseCase.transfer(new TransferOwnershipCommand(acting(yeoreum), dohyun)),
                () -> approveOrganizerChangeRequestUseCase.approve(new ApproveOrganizerChangeRequestCommand(admin, requestId)),
                () -> {
                    assertOrganizerRowFreeForForeignKeyChecks();
                    assertRequestRowFree(requestId);
                });

        assertFailedWith(approval, RuleViolationException.class, OrganizerErrorCode.CHANGE_REQUESTER_NOT_OWNER);
        assertThat(currentOwner()).isEqualTo(dohyun);
        assertThat(storedAccountNumber()).isEqualTo("110-123-456789");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM organizer_change_request WHERE id = ?", String.class,
                requestId)).isEqualTo("UNDER_REVIEW");
    }

    @Test
    @DisplayName("주최자명 변경이 주최자 줄을 잠근 순간 들어온 변경 승인은 요청 줄을 고치기 전에 주최자 줄에서 기다린다 — 둘 다 끝까지 처리된다")
    void approvalWaitsOnOrganizerRowBeforeTouchingRequestRow() throws Exception {
        long requestId = requestAccountChange();
        String newName = "늦여름 레코드 " + suffix;

        Future<?> approval = runInterleaved(
                () -> {
                    StaffAccountLocks.lockForReferenceInIdOrder(staffAccountRepository, yeoreum);
                    organizerRepository.findByIdForUpdate(lateSummer);
                },
                () -> renameOrganizerUseCase.rename(new RenameOrganizerCommand(acting(yeoreum), OrganizerName.of(newName))),
                () -> approveOrganizerChangeRequestUseCase.approve(new ApproveOrganizerChangeRequestCommand(admin, requestId)),
                () -> assertRequestRowFree(requestId));

        approval.get(30, SECONDS);
        assertThat(storedAccountNumber()).isEqualTo("1002-345-678901");
        assertThat(jdbcTemplate.queryForObject("SELECT name FROM organizer WHERE id = ?", String.class, lateSummer))
                .isEqualTo(newName);
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM organizer_change_request WHERE id = ?", String.class,
                requestId)).isEqualTo("APPROVED");
    }

    /**
     * 붙잡을 흐름의 첫 잠금만 한 트랜잭션에서 걸어 둔 채 다른 스레드에서 맞서는 흐름을 부른다. 맞서는 흐름이 잠금을 기다리기 시작하면 그 순간의
     * 확인을 하고, 같은 트랜잭션에서 붙잡을 흐름을 끝까지 돌려 커밋한다. 붙잡은 흐름이 실패(교착 40P01 포함)하면 그 예외로 테스트가 실패한다.
     *
     * @return 맞서는 흐름 — 끝난 뒤에 돌려준다. 성공했는지 막혔는지는 부르는 쪽이 본다.
     */
    private Future<?> runInterleaved(Runnable firstLocks, Runnable heldFlow, Runnable contender,
            Runnable whileContenderWaits) throws Exception {
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch mayContinue = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> held = executor.submit(() -> new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status -> {
                        firstLocks.run();
                        firstLocked.countDown();
                        awaitOrFail(mayContinue);
                        heldFlow.run();
                    }));
            awaitOrFail(firstLocked);
            Future<?> contention = executor.submit(contender);
            awaitLockWaiter();
            whileContenderWaits.run();
            mayContinue.countDown();
            held.get(30, SECONDS);
            executor.shutdown();
            assertThat(executor.awaitTermination(30, SECONDS)).as("맞서는 흐름이 30초 안에 끝난다").isTrue();
            return contention;
        } finally {
            mayContinue.countDown();
            executor.shutdown();
        }
    }

    /** 맞서는 흐름이 기다리는 동안 주최자 줄은 FOR UPDATE로 잠겨 있지 않다 — 붙잡은 흐름이 외래 키 확인 잠금을 바로 걸 수 있다. */
    private void assertOrganizerRowFreeForForeignKeyChecks() {
        assertThatNoException().isThrownBy(() -> jdbcTemplate.queryForObject(
                "SELECT id FROM organizer WHERE id = ? FOR KEY SHARE NOWAIT", Long.class, lateSummer));
    }

    /** 맞서는 흐름이 기다리는 동안 변경 요청 줄은 아직 잠겨 있지 않다 — 승인은 요청 줄을 고치기 전에 앞 차례의 줄에서 기다린다. */
    private void assertRequestRowFree(long requestId) {
        assertThatNoException().isThrownBy(() -> jdbcTemplate.queryForObject(
                "SELECT id FROM organizer_change_request WHERE id = ? FOR UPDATE NOWAIT", Long.class, requestId));
    }

    private static void assertFailedWith(Future<?> contention, Class<? extends DomainException> type,
            ErrorCode expectedCode) {
        assertThatThrownBy(() -> contention.get(30, SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(type)
                .satisfies(exception -> assertThat(((DomainException) exception.getCause()).errorCode())
                        .isEqualTo(expectedCode));
    }

    private long requestAccountChange() {
        return requestOrganizerChangeUseCase.request(new RequestOrganizerChangeCommand(acting(yeoreum),
                new RequestedOrganizerChange.NewSettlementAccount(WOORI))).changeRequestId();
    }

    /** 다른 트랜잭션의 잠금을 기다리는 연결이 생길 때까지 기다린다 — 맞서는 흐름이 잠금에서 멈춘 것. */
    private void awaitLockWaiter() throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            Long waiters = jdbcTemplate.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted", Long.class);
            if (waiters != null && waiters > 0) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("맞서는 흐름이 10초 안에 잠금을 기다리지 않았습니다");
    }

    private static void awaitOrFail(CountDownLatch latch) {
        try {
            if (!latch.await(30, SECONDS)) {
                throw new AssertionError("기다리는 신호가 30초 안에 오지 않았습니다");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("신호를 기다리다 끊겼습니다", exception);
        }
    }

    private ActingStaff acting(long staffAccountId) {
        return new ActingStaff(AccountRef.of(AccountKind.STAFF, staffAccountId), lateSummer);
    }

    private Long currentOwner() {
        return jdbcTemplate.queryForObject("""
                SELECT staff_account_id FROM staff_membership
                WHERE organizer_id = ? AND role = 'OWNER' AND ended_at IS NULL""", Long.class, lateSummer);
    }

    private String storedAccountNumber() {
        return jdbcTemplate.queryForObject("SELECT settlement_account_number FROM organizer WHERE id = ?", String.class,
                lateSummer);
    }

    private long changeRequestCount() {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM organizer_change_request WHERE organizer_id = ?",
                Long.class, lateSummer);
        return count == null ? 0 : count;
    }

    /** 이메일 인증을 마친 운영진 로그인 한 줄 — 이 테스트는 로그인하지 않아 비밀번호 해시는 아무 값이다. */
    private long storeStaff(String name, String nickname, String email) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO staff_account (status, name, nickname, email, email_lowercase, password_hash, signed_up_at,
                                           email_verified_at)
                VALUES ('ACTIVE', ?, ?, ?, lower(?), '{test}hashed-password', now() - interval '30 days',
                        now() - interval '30 days')
                RETURNING id""", Long.class, name, nickname, email, email);
        assertThat(id).isNotNull();
        return id;
    }

    /** 승인된 사업자 주최자 한 줄 — 사업자 정보 · 정산 계좌(신한은행)까지 표에 바로 넣는다. */
    private long storeBusinessOrganizer(String name) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO organizer (name, status, business_status, business_trade_name, business_representative_name,
                                       business_address, business_phone_number, business_registration_number,
                                       settlement_bank_name, settlement_account_number, settlement_account_holder)
                VALUES (?, 'ACTIVE', 'BUSINESS', '늦여름 레코드', '최여름', '서울특별시 마포구 와우산로 12', '02-123-4567',
                        '1234567890', '신한은행', '110-123-456789', '늦여름 레코드')
                RETURNING id""", Long.class, name);
        assertThat(id).isNotNull();
        return id;
    }

    private void storeMembership(long organizerId, long staffAccountId, String role) {
        jdbcTemplate.update("""
                INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at)
                VALUES (?, ?, ?, now() - interval '10 days')""", organizerId, staffAccountId, role);
    }

    /** 관리자 계정 한 줄 — 이 테스트는 로그인하지 않아 비밀번호 해시는 아무 값이다. */
    private long storeAdmin(String email) {
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO admin_account (grade, email, email_lowercase, password_hash, issued_at)
                VALUES ('ADMIN', ?, lower(?), '{test}hashed-password', now())
                RETURNING id""", Long.class, email, email);
        assertThat(id).isNotNull();
        return id;
    }
}
