package com.ticketon.persistence.organizer;

import static com.ticketon.persistence.jooq.Tables.ORGANIZER;
import static com.ticketon.persistence.jooq.Tables.ORGANIZER_SNS_LINK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.organizer.BusinessDeclaration;
import com.ticketon.domain.organizer.BusinessInformation;
import com.ticketon.domain.organizer.BusinessRegistrationNumber;
import com.ticketon.domain.organizer.ExternalLink;
import com.ticketon.domain.organizer.Organizer;
import com.ticketon.domain.organizer.OrganizerBusinessDetails;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerLogo;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.OrganizerPublicProfile;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.SettlementAccount;
import com.ticketon.domain.organizer.TelephoneNumber;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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
 * 진짜 PostgreSQL에 마이그레이션(V8 주최자, V16 공개 정보, V18 사업자 정보 · 정산 계좌)을 올리고 새 주최자 저장, 다시 읽기, 승인 저장(심사
 * 중일 때만, 사업자 정보 · 정산 계좌 함께), 승인한 변경 저장, 공개 정보 저장(소개 · SNS 링크 · 로고 열쇠), 주최자명 저장을 본다.
 * Docker가 없는 환경에서는 건너뛴다.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("주최자 저장소 (진짜 PostgreSQL)")
class OrganizerPersistenceAdapterTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final OrganizerLogo LOGO = new OrganizerLogo("twelveMonthsLogoAAAAAAAAAAAAAAAA.png");

    /** 극단 열두달 — 비사업자, 국민은행 계좌. */
    private static final OrganizerBusinessDetails TWELVE_MONTHS_DETAILS = new OrganizerBusinessDetails(
            BusinessDeclaration.nonBusiness(), new SettlementAccount("국민은행", "123456-78-901234", "박정훈"));

    /** 늦여름 — 사업자, 신한은행 계좌. */
    private static final OrganizerBusinessDetails LATE_SUMMER_DETAILS = new OrganizerBusinessDetails(
            BusinessDeclaration.business(new BusinessInformation("늦여름 레코드", "최여름", "서울특별시 마포구 와우산로 12",
                    TelephoneNumber.of("02-123-4567"), BusinessRegistrationNumber.of("123-45-67890"))),
            new SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드"));

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

    private long activeOrganizer(String name) {
        return new OrganizerRows(dsl).organizer(name, OrganizerStatus.ACTIVE);
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
    @DisplayName("저장한 주최자를 id로 다시 읽는다 — 공개 정보를 적지 않았으면 비어 있다")
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
    @DisplayName("승인한 주최자를 저장하면 표의 상태가 '활동'이 되고, 승인한 신청서의 사업자 정보 · 정산 계좌가 함께 적힌다")
    void savesApproval() {
        long id = adapter.add(Organizer.underReview(OrganizerName.of("극단 열두달"))).id().getAsLong();
        Organizer organizer = adapter.findById(id).orElseThrow();
        organizer.approve();
        assertThat(adapter.findBusinessDetails(id)).isEmpty();

        adapter.saveApproval(organizer, TWELVE_MONTHS_DETAILS);

        assertThat(adapter.findById(id).orElseThrow().status()).isEqualTo(OrganizerStatus.ACTIVE);
        assertThat(adapter.findBusinessDetails(id)).contains(TWELVE_MONTHS_DETAILS);
    }

    @Test
    @DisplayName("사업자 주최자는 사업자 정보 다섯 칸까지 적고 그대로 다시 읽는다 — 사업자등록번호는 숫자만 10자리다")
    void savesBusinessOrganizerDetails() {
        long id = adapter.add(Organizer.underReview(OrganizerName.of("늦여름"))).id().getAsLong();
        Organizer organizer = adapter.findById(id).orElseThrow();
        organizer.approve();

        adapter.saveApproval(organizer, LATE_SUMMER_DETAILS);

        assertThat(adapter.findBusinessDetails(id)).contains(LATE_SUMMER_DETAILS);
        var row = dsl.selectFrom(ORGANIZER).where(ORGANIZER.ID.eq(id)).fetchSingle();
        assertThat(row.getBusinessStatus()).isEqualTo("BUSINESS");
        assertThat(row.getBusinessRegistrationNumber()).isEqualTo("1234567890");
        assertThat(row.getSettlementAccountNumber()).isEqualTo("110-123-456789");
    }

    @Test
    @DisplayName("승인한 변경을 저장하면 사업자 정보 · 정산 계좌 칸만 바뀌고 이름 · 상태 · 공개 정보는 그대로다")
    void savesBusinessDetailsOnly() {
        long id = adapter.add(Organizer.underReview(OrganizerName.of("늦여름"))).id().getAsLong();
        Organizer organizer = adapter.findById(id).orElseThrow();
        organizer.approve();
        adapter.saveApproval(organizer, LATE_SUMMER_DETAILS);
        OrganizerBusinessDetails changed = LATE_SUMMER_DETAILS
                .withSettlementAccount(new SettlementAccount("우리은행", "1002-345-678901", "최여름"));

        adapter.saveBusinessDetails(id, changed);

        assertThat(adapter.findBusinessDetails(id)).contains(changed);
        Organizer found = adapter.findById(id).orElseThrow();
        assertThat(found.name()).isEqualTo(OrganizerName.of("늦여름"));
        assertThat(found.status()).isEqualTo(OrganizerStatus.ACTIVE);
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.saveBusinessDetails(id + 1000, changed));
    }

    @Test
    @DisplayName("사업자 여부 · 사업자 정보 칸이 맞지 않거나 정산 계좌 칸이 일부만 있는 줄은 표가 받지 않는다")
    void tableRejectsInconsistentBusinessColumns() {
        long id = activeOrganizer("극단 열두달");

        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> dsl.update(ORGANIZER)
                .set(ORGANIZER.BUSINESS_STATUS, "BUSINESS")
                .set(ORGANIZER.SETTLEMENT_BANK_NAME, "국민은행")
                .set(ORGANIZER.SETTLEMENT_ACCOUNT_NUMBER, "123456-78-901234")
                .set(ORGANIZER.SETTLEMENT_ACCOUNT_HOLDER, "박정훈")
                .where(ORGANIZER.ID.eq(id))
                .execute());
        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> dsl.update(ORGANIZER)
                .set(ORGANIZER.BUSINESS_STATUS, "NON_BUSINESS")
                .set(ORGANIZER.SETTLEMENT_BANK_NAME, "국민은행")
                .where(ORGANIZER.ID.eq(id))
                .execute());
        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> dsl.update(ORGANIZER)
                .set(ORGANIZER.SETTLEMENT_BANK_NAME, "국민은행")
                .set(ORGANIZER.SETTLEMENT_ACCOUNT_NUMBER, "123456-78-901234")
                .set(ORGANIZER.SETTLEMENT_ACCOUNT_HOLDER, "박정훈")
                .where(ORGANIZER.ID.eq(id))
                .execute());
        assertThat(adapter.findBusinessDetails(id)).isEmpty();
    }

    @Test
    @DisplayName("읽은 뒤 표에서 이미 심사 중이 아니게 됐으면 충돌이고 덮어쓰지 않는다")
    void approvalOfNoLongerUnderReviewIsConflict() {
        long id = adapter.add(Organizer.underReview(OrganizerName.of("극단 열두달"))).id().getAsLong();
        Organizer organizer = adapter.findById(id).orElseThrow();
        organizer.approve();
        dsl.update(ORGANIZER).set(ORGANIZER.STATUS, OrganizerStatus.CLOSED.name()).where(ORGANIZER.ID.eq(id)).execute();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> adapter.saveApproval(organizer, TWELVE_MONTHS_DETAILS))
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

        assertThatIllegalArgumentException().isThrownBy(() -> adapter.saveApproval(underReview, TWELVE_MONTHS_DETAILS));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.saveApproval(unsaved, TWELVE_MONTHS_DETAILS));
        assertThat(adapter.findById(id).orElseThrow().status()).isEqualTo(OrganizerStatus.UNDER_REVIEW);
    }

    @Test
    @DisplayName("공개 정보를 저장하면 소개 · 로고 열쇠 · SNS 링크(적은 순서대로)를 다시 읽는다 — 이름 · 상태는 건드리지 않는다")
    void savesPublicProfile() {
        long id = activeOrganizer("극단 열두달");
        Organizer organizer = adapter.findById(id).orElseThrow();
        organizer.changeIntroductionAndSnsLinks(" 2019년부터 활동한 소극장 극단 ",
                List.of(ExternalLink.of("https://www.instagram.com/twelve_months_theater"),
                        ExternalLink.of("https://blog.example.com/twelve-months")));
        organizer.changeLogo(LOGO);
        dsl.update(ORGANIZER).set(ORGANIZER.NAME, "극단 열두달 프로젝트").where(ORGANIZER.ID.eq(id)).execute();

        adapter.savePublicProfile(organizer);

        Organizer found = adapter.findById(id).orElseThrow();
        assertThat(found.publicProfile()).isEqualTo(new OrganizerPublicProfile("2019년부터 활동한 소극장 극단",
                List.of(ExternalLink.of("https://www.instagram.com/twelve_months_theater"),
                        ExternalLink.of("https://blog.example.com/twelve-months")), LOGO));
        assertThat(found.name()).isEqualTo(OrganizerName.of("극단 열두달 프로젝트"));
        assertThat(found.status()).isEqualTo(OrganizerStatus.ACTIVE);
    }

    @Test
    @DisplayName("소개를 비우고 링크를 줄이고 로고를 지워 저장하면 그대로 남는다 — 비운 소개 · 지운 로고는 NULL이다")
    void savesClearedPublicProfile() {
        long id = activeOrganizer("극단 열두달");
        Organizer organizer = adapter.findById(id).orElseThrow();
        organizer.changeIntroductionAndSnsLinks("소개", List.of(ExternalLink.of("https://a.example.com"),
                ExternalLink.of("https://b.example.com")));
        organizer.changeLogo(LOGO);
        adapter.savePublicProfile(organizer);

        Organizer changed = adapter.findById(id).orElseThrow();
        changed.changeIntroductionAndSnsLinks(null, List.of(ExternalLink.of("https://b.example.com")));
        changed.removeLogo();
        adapter.savePublicProfile(changed);

        Organizer found = adapter.findById(id).orElseThrow();
        assertThat(found.publicProfile()).isEqualTo(
                new OrganizerPublicProfile(null, List.of(ExternalLink.of("https://b.example.com")), null));
        var row = dsl.selectFrom(ORGANIZER).where(ORGANIZER.ID.eq(id)).fetchSingle();
        assertThat(row.getIntroduction()).isNull();
        assertThat(row.getLogoFileKey()).isNull();
        assertThat(dsl.fetchCount(ORGANIZER_SNS_LINK, ORGANIZER_SNS_LINK.ORGANIZER_ID.eq(id))).isEqualTo(1);
    }

    @Test
    @DisplayName("한 주최자의 공개 정보를 저장해도 다른 주최자의 SNS 링크는 그대로다")
    void savingKeepsOtherOrganizersLinks() {
        long twelveMonths = activeOrganizer("극단 열두달");
        long lateSummer = activeOrganizer("늦여름");
        Organizer other = adapter.findById(lateSummer).orElseThrow();
        other.changeIntroductionAndSnsLinks(null, List.of(ExternalLink.of("https://late-summer.example.com")));
        adapter.savePublicProfile(other);

        Organizer organizer = adapter.findById(twelveMonths).orElseThrow();
        organizer.changeIntroductionAndSnsLinks(null, List.of());
        adapter.savePublicProfile(organizer);

        assertThat(adapter.findById(lateSummer).orElseThrow().publicProfile().snsLinks())
                .containsExactly(ExternalLink.of("https://late-summer.example.com"));
    }

    @Test
    @DisplayName("잠그고 읽어도 같은 주최자를 공개 정보와 함께 읽는다")
    void findsByIdForUpdate() {
        long id = activeOrganizer("극단 열두달");
        Organizer organizer = adapter.findById(id).orElseThrow();
        organizer.changeLogo(LOGO);
        adapter.savePublicProfile(organizer);

        Organizer locked = adapter.findByIdForUpdate(id).orElseThrow();

        assertThat(locked.id()).hasValue(id);
        assertThat(locked.publicProfile().logo()).isEqualTo(LOGO);
        assertThat(adapter.findByIdForUpdate(id + 1000)).isEmpty();
    }

    @Test
    @DisplayName("외래 키 확인 잠금으로 읽는 동안 다른 트랜잭션은 그 줄을 FOR UPDATE로 잠그지 못하고, 외래 키 확인 잠금은 함께 걸 수 있다")
    void findByIdForKeyShareBlocksOnlyForUpdate() throws Exception {
        long id = activeOrganizer("극단 열두달");
        assertThat(adapter.findByIdForKeyShare(id).orElseThrow().name()).isEqualTo(OrganizerName.of("극단 열두달"));
        assertThat(adapter.findByIdForKeyShare(id + 1000)).isEmpty();
        CountDownLatch keyShared = new CountDownLatch(1);
        CountDownLatch checked = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Boolean> holder = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                boolean found = transactionAdapter.findByIdForKeyShare(id).isPresent();
                keyShared.countDown();
                assertThat(checked.await(10, TimeUnit.SECONDS)).isTrue();
                return found;
            }));
            assertThat(keyShared.await(10, TimeUnit.SECONDS)).isTrue();

            assertThatExceptionOfType(DataAccessException.class)
                    .isThrownBy(() -> dsl.selectFrom(ORGANIZER).where(ORGANIZER.ID.eq(id)).forUpdate().noWait().fetch())
                    .satisfies(lockNotAvailable -> assertThat(lockNotAvailable.sqlState()).isEqualTo("55P03"));
            assertThat(dsl.selectFrom(ORGANIZER).where(ORGANIZER.ID.eq(id)).forKeyShare().noWait().fetch()).hasSize(1);
            checked.countDown();

            assertThat(holder.get(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            checked.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("잠그고 읽으면 다른 운영진이 로고를 바꾸는 중인 주최자는 그쪽이 커밋할 때까지 기다렸다가 바뀐 로고를 읽는다")
    void lockedReadWaitsForCommittedLogoChange() throws Exception {
        long id = activeOrganizer("극단 열두달");
        CountDownLatch firstChanged = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<OrganizerLogo> first = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                Organizer organizer = transactionAdapter.findByIdForUpdate(id).orElseThrow();
                organizer.changeLogo(LOGO);
                transactionAdapter.savePublicProfile(organizer);
                firstChanged.countDown();
                assertThat(secondStarted.await(10, TimeUnit.SECONDS)).isTrue();
                Thread.sleep(300);
                return organizer.publicProfile().logo();
            }));
            assertThat(firstChanged.await(10, TimeUnit.SECONDS)).isTrue();
            Future<OrganizerLogo> second = executor.submit(() -> inOwnTransaction(transactionAdapter -> {
                secondStarted.countDown();
                return transactionAdapter.findByIdForUpdate(id).orElseThrow().publicProfile().logo();
            }));

            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo(LOGO);
            assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo(LOGO);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("바꾼 주최자명을 저장하면 이름만 바뀐다 — 앞뒤 공백을 걷은 이름이 남고 상태 · 공개 정보는 그대로다")
    void savesName() {
        long id = activeOrganizer("극단 열두달");
        Organizer organizer = adapter.findById(id).orElseThrow();
        organizer.changeIntroductionAndSnsLinks("소개", List.of(ExternalLink.of("https://a.example.com")));
        organizer.changeLogo(LOGO);
        adapter.savePublicProfile(organizer);
        Organizer locked = adapter.findByIdForUpdate(id).orElseThrow();
        locked.rename(OrganizerName.of(" 열두달 프로젝트 "), 1, Instant.parse("2026-10-15T01:00:00Z"));

        adapter.saveName(locked);

        Organizer found = adapter.findById(id).orElseThrow();
        assertThat(found.name()).isEqualTo(OrganizerName.of("열두달 프로젝트"));
        assertThat(dsl.selectFrom(ORGANIZER).where(ORGANIZER.ID.eq(id)).fetchSingle().getName()).isEqualTo("열두달 프로젝트");
        assertThat(found.status()).isEqualTo(OrganizerStatus.ACTIVE);
        assertThat(found.publicProfile()).isEqualTo(new OrganizerPublicProfile("소개",
                List.of(ExternalLink.of("https://a.example.com")), LOGO));
    }

    @Test
    @DisplayName("저장 전 주최자나 표에 없는 주최자의 이름은 저장하지 않는다")
    void requiresStoredOrganizerForName() {
        Organizer unsaved = Organizer.underReview(OrganizerName.of("늦여름"));
        Organizer missing = Organizer.restore(999_999, OrganizerName.of("늦여름"), OrganizerStatus.ACTIVE,
                OrganizerPublicProfile.empty());

        assertThatIllegalArgumentException().isThrownBy(() -> adapter.saveName(unsaved));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.saveName(missing));
    }

    @Test
    @DisplayName("저장 전 주최자나 표에 없는 주최자의 공개 정보는 저장하지 않는다")
    void requiresStoredOrganizerForPublicProfile() {
        Organizer unsaved = Organizer.underReview(OrganizerName.of("늦여름"));
        Organizer missing = Organizer.restore(999_999, OrganizerName.of("늦여름"), OrganizerStatus.ACTIVE,
                OrganizerPublicProfile.empty());

        assertThatIllegalArgumentException().isThrownBy(() -> adapter.savePublicProfile(unsaved));
        assertThatIllegalArgumentException().isThrownBy(() -> adapter.savePublicProfile(missing));
    }

    @Test
    @DisplayName("같은 로고 열쇠는 두 주최자가 함께 가리킬 수 없다 — 지운 로고의 파일이 다른 주최자의 로고가 아니게")
    void logoFileKeyIsUnique() {
        long twelveMonths = activeOrganizer("극단 열두달");
        long lateSummer = activeOrganizer("늦여름");
        Organizer first = adapter.findById(twelveMonths).orElseThrow();
        first.changeLogo(LOGO);
        adapter.savePublicProfile(first);
        Organizer second = adapter.findById(lateSummer).orElseThrow();
        second.changeLogo(LOGO);

        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> adapter.savePublicProfile(second));
    }

    @Test
    @DisplayName("표는 빈 글자 소개를 받지 않는다 — 비운 소개는 NULL이다")
    void emptyIntroductionIsRejectedByTable() {
        long id = activeOrganizer("극단 열두달");

        assertThatExceptionOfType(DataAccessException.class).isThrownBy(() -> dsl.update(ORGANIZER)
                .set(ORGANIZER.INTRODUCTION, "")
                .where(ORGANIZER.ID.eq(id))
                .execute());
    }

    @Test
    @DisplayName("이미 저장된 주최자는 새 주최자로 저장하지 않는다")
    void rejectsSavedOrganizer() {
        Organizer saved = Organizer.restore(5, OrganizerName.of("극단 열두달"), OrganizerStatus.UNDER_REVIEW,
                OrganizerPublicProfile.empty());

        assertThatIllegalArgumentException().isThrownBy(() -> adapter.add(saved));
        assertThat(dsl.fetchCount(ORGANIZER)).isZero();
    }

    /** 제 연결 · 트랜잭션에서 저장소를 써서 일하고 커밋한다 — 두 트랜잭션의 잠금을 보려고 연결을 따로 연다. */
    private static <T> T inOwnTransaction(TransactionWork<T> work) {
        try (CloseableDSLContext ownConnection = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword())) {
            return ownConnection.transactionResult(
                    transaction -> work.run(new OrganizerPersistenceAdapter(transaction.dsl())));
        }
    }

    @FunctionalInterface
    private interface TransactionWork<T> {

        T run(OrganizerPersistenceAdapter transactionAdapter) throws Exception;
    }
}
