package com.ticketon.api.organizer;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.ticketon.application.account.port.out.StaffAccountRepository;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.InviteOperatorCommand;
import com.ticketon.application.organizer.port.in.InviteOperatorUseCase;
import com.ticketon.application.organizer.port.in.RemoveOperatorCommand;
import com.ticketon.application.organizer.port.in.RemoveOperatorUseCase;
import com.ticketon.application.organizer.port.in.RenameOrganizerCommand;
import com.ticketon.application.organizer.port.in.RenameOrganizerUseCase;
import com.ticketon.application.organizer.port.in.TransferOwnershipCommand;
import com.ticketon.application.organizer.port.in.TransferOwnershipUseCase;
import com.ticketon.application.organizer.port.in.UploadOrganizerLogoCommand;
import com.ticketon.application.organizer.port.in.UploadOrganizerLogoUseCase;
import com.ticketon.application.organizer.service.StaffAccountLocks;
import com.ticketon.common.TestImages;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.organizer.OrganizerLogoImage;
import com.ticketon.domain.organizer.OrganizerName;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 진짜 PostgreSQL로 주최자 줄을 잠그는 흐름(주최자명 변경, 로고 올리기)과 운영진 계정 줄을 먼저 잠그는 흐름(소유자 넘기기, 내보내기) · 계정 줄을
 * 잠그지 않는 흐름(운영자 초대)이 가장 나쁜 순간에 엇갈려도 교착(40P01) 없이 차례로 처리되는 것을 고정한다(#195, 잠금 순서 —
 * {@link StaffAccountLocks}). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 가장 나쁜 순간은 한 흐름이 첫 잠금만 쥐고 다음 잠금으로 가기 직전이다. 붙잡을 흐름의 첫 잠금만 한 트랜잭션에서 걸어 둔 채 다른 스레드에서 맞서는
 * 흐름을 부르고, 맞서는 흐름이 잠금을 기다리기 시작한 것을 PostgreSQL의 잠금 표(pg_locks)로 확인한다. 그 순간 맞서는 흐름이 쥐지 않았어야 할 줄을
 * NOWAIT로 잡아 보고, 같은 트랜잭션에서 붙잡을 흐름을 끝까지 돌려 커밋한다 — 유스케이스는 바깥 트랜잭션에 참여하므로 첫 잠금은 이미 쥔 줄이다.
 * 주최자 줄을 먼저 잡던 예전 순서였다면 맞서는 흐름이 주최자 줄을 쥔 채 계정 줄을 기다려, 확인이 실패하거나 붙잡은 흐름이 주최자 줄에 외래 키 확인
 * 잠금을 거는 순간 교착한다.
 * <p>
 * 극단 열두달(docs/15)의 소유자 박정훈, 운영자 기획 단원 · 홍보 단원. 운영진 로그인 · 주최자 · 소속은 표에 바로 넣는다. 로고는 임시 디렉터리에
 * 저장한다.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자 줄 · 운영진 계정 줄 잠금 순서 — 가장 나쁜 순간에 엇갈려도 교착 없이 차례로 (진짜 PostgreSQL)")
class OrganizerLockOrderRaceIntegrationTest {

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @TempDir
    private static Path storageDirectory;

    @DynamicPropertySource
    static void localStorage(DynamicPropertyRegistry registry) {
        registry.add("ticketon.storage.local.directory", () -> storageDirectory.toString());
        registry.add("ticketon.storage.local.public-base-url", () -> "http://files.ticket-on.test/api/public/files");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private StaffAccountRepository staffAccountRepository;

    @Autowired
    private RenameOrganizerUseCase renameOrganizerUseCase;

    @Autowired
    private TransferOwnershipUseCase transferOwnershipUseCase;

    @Autowired
    private UploadOrganizerLogoUseCase uploadOrganizerLogoUseCase;

    @Autowired
    private RemoveOperatorUseCase removeOperatorUseCase;

    @Autowired
    private InviteOperatorUseCase inviteOperatorUseCase;

    private String suffix;
    private long twelveMonths;
    private long junghoon;
    private long planner;
    private long promoter;

    @BeforeEach
    void storeTwelveMonths() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoon = storeStaff("박정훈", "고래" + suffix, "junghoon-" + suffix + "@example.com");
        planner = storeStaff("김기획", "바람" + suffix, "planner-" + suffix + "@example.com");
        promoter = storeStaff("정홍보", "구름" + suffix, "promoter-" + suffix + "@example.com");
        twelveMonths = storeOrganizer("극단 열두달 " + suffix);
        storeMembership(twelveMonths, junghoon, "OWNER");
        storeMembership(twelveMonths, planner, "OPERATOR");
        storeMembership(twelveMonths, promoter, "OPERATOR");
    }

    @Test
    @DisplayName("소유자 넘기기가 계정 줄을 잠근 순간 들어온 주최자명 변경은 주최자 줄을 잡기 전에 계정 줄에서 기다린다 — 둘 다 끝까지 처리된다")
    void renameWaitsOnAccountRowWhileTransferHoldsIt() throws Exception {
        String newName = "열두달 프로젝트 " + suffix;

        runInterleaved(
                () -> StaffAccountLocks.lockInIdOrder(staffAccountRepository, junghoon, promoter),
                () -> transferOwnershipUseCase.transfer(new TransferOwnershipCommand(acting(junghoon), promoter)),
                () -> renameOrganizerUseCase.rename(new RenameOrganizerCommand(acting(junghoon), OrganizerName.of(newName))),
                this::assertOrganizerRowFreeForForeignKeyChecks);

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM organizer WHERE id = ?", String.class, twelveMonths))
                .isEqualTo(newName);
        assertThat(currentOwner()).isEqualTo(promoter);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT changed_by_staff_account_id FROM organizer_name_change WHERE organizer_id = ?", Long.class,
                twelveMonths)).isEqualTo(junghoon);
        assertThat(rowCount("ownership_change")).isEqualTo(1);
        assertThat(rowCount("staff_operation_record")).isEqualTo(2);
    }

    @Test
    @DisplayName("운영자 내보내기가 계정 줄을 잠근 순간 들어온 로고 올리기는 주최자 줄을 잡기 전에 계정 줄에서 기다린다 — 둘 다 끝까지 처리된다")
    void logoUploadWaitsOnAccountRowWhileRemovalHoldsIt() throws Exception {
        runInterleaved(
                () -> StaffAccountLocks.lockInIdOrder(staffAccountRepository, junghoon, planner),
                () -> removeOperatorUseCase.remove(new RemoveOperatorCommand(acting(junghoon), planner)),
                () -> uploadOrganizerLogoUseCase.upload(new UploadOrganizerLogoCommand(acting(junghoon),
                        OrganizerLogoImage.of("image/png", TestImages.png(64, 64)))),
                this::assertOrganizerRowFreeForForeignKeyChecks);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM staff_membership WHERE staff_account_id = ? AND ended_at IS NULL", Long.class,
                planner)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT logo_file_key FROM organizer WHERE id = ?", String.class,
                twelveMonths)).isNotNull();
        assertThat(rowCount("staff_operation_record")).isEqualTo(2);
    }

    @Test
    @DisplayName("운영자 초대가 주최자 줄에 외래 키 확인 잠금을 건 순간 들어온 로고 올리기는 계정 줄을 FOR UPDATE로 잡지 않아 초대를 막지 않는다 — 둘 다 끝까지 처리된다")
    void logoUploadDoesNotBlockInvitationOnAccountRow() throws Exception {
        String inviteeEmail = "dohyun-" + suffix + "@example.com";

        runInterleaved(
                () -> jdbcTemplate.queryForObject("SELECT id FROM organizer WHERE id = ? FOR KEY SHARE", Long.class,
                        twelveMonths),
                () -> inviteOperatorUseCase.invite(new InviteOperatorCommand(acting(junghoon), EmailAddress.of(inviteeEmail))),
                () -> uploadOrganizerLogoUseCase.upload(new UploadOrganizerLogoCommand(acting(junghoon),
                        OrganizerLogoImage.of("image/png", TestImages.png(64, 64)))),
                () -> assertThatNoException().isThrownBy(() -> jdbcTemplate.queryForObject(
                        "SELECT id FROM staff_account WHERE id = ? FOR KEY SHARE NOWAIT", Long.class, junghoon)));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM staff_invitation WHERE organizer_id = ? AND status = 'PENDING'", Long.class,
                twelveMonths)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT logo_file_key FROM organizer WHERE id = ?", String.class,
                twelveMonths)).isNotNull();
        assertThat(rowCount("staff_operation_record")).isEqualTo(2);
    }

    /**
     * 붙잡을 흐름의 첫 잠금만 한 트랜잭션에서 걸어 둔 채 다른 스레드에서 맞서는 흐름을 부른다. 맞서는 흐름이 잠금을 기다리기 시작하면 그 순간의
     * 확인을 하고, 같은 트랜잭션에서 붙잡을 흐름을 끝까지 돌려 커밋한다. 두 흐름이 모두 끝나야 돌아온다 — 어느 쪽이든 실패(교착 40P01 포함)하면 그
     * 예외로 테스트가 실패한다.
     */
    private void runInterleaved(Runnable firstLocks, Runnable heldFlow, Runnable contender, Runnable whileContenderWaits)
            throws Exception {
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
            contention.get(30, SECONDS);
        } finally {
            mayContinue.countDown();
            executor.shutdown();
        }
    }

    /**
     * 맞서는 흐름이 기다리는 동안 주최자 줄은 FOR UPDATE로 잠겨 있지 않다 — 붙잡은 흐름이 소속 · 이력 · 조작 기록을 넣으며 거는 외래 키 확인
     * 잠금을 바로 걸 수 있다. 맞서는 흐름이 주최자 줄을 먼저 잡았다면 여기서 잠금을 얻지 못한다.
     */
    private void assertOrganizerRowFreeForForeignKeyChecks() {
        assertThatNoException().isThrownBy(() -> jdbcTemplate.queryForObject(
                "SELECT id FROM organizer WHERE id = ? FOR KEY SHARE NOWAIT", Long.class, twelveMonths));
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
        return new ActingStaff(AccountRef.of(AccountKind.STAFF, staffAccountId), twelveMonths);
    }

    private Long currentOwner() {
        return jdbcTemplate.queryForObject("""
                SELECT staff_account_id FROM staff_membership
                WHERE organizer_id = ? AND role = 'OWNER' AND ended_at IS NULL""", Long.class, twelveMonths);
    }

    /** 그 표에서 이 주최자의 줄 수 — 이력 · 조작 기록이 흐름마다 한 줄씩 남았는지 본다. */
    private long rowCount(String organizerTable) {
        Long count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + organizerTable + " WHERE organizer_id = ?",
                Long.class, twelveMonths);
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

    private long storeOrganizer(String name) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class, name);
        assertThat(id).isNotNull();
        return id;
    }

    private void storeMembership(long organizerId, long staffAccountId, String role) {
        jdbcTemplate.update("""
                INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at)
                VALUES (?, ?, ?, now() - interval '10 days')""", organizerId, staffAccountId, role);
    }
}
