package com.ticketon.api.organizer;

import static com.ticketon.api.error.ErrorResponseMatchers.errorBody;
import static com.ticketon.api.security.TestLogin.loggedInAs;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ticketon.application.organizer.port.in.GetStaffOperationHistoryQuery;
import com.ticketon.application.organizer.port.in.GetStaffOperationHistoryUseCase;
import com.ticketon.application.organizer.port.in.StaffOperationHistoryResult;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 서버 전체를 진짜 PostgreSQL(마이그레이션 전체)로 띄워, 주최 관리 기능이 쓸 공통 방식 그대로 — 경로의 주최자 id와 로그인한 운영진을 받아
 * 한 트랜잭션에서 권한을 확인하고 조작 기록을 남기는 — 시험 조작({@link StaffOperationTestService})을 끝까지 돌린다(#24 완료 조건: 소유자/운영자
 * 권한 표대로 허용·거부되고 조작 기록이 남는다). Docker가 없는 환경에서는 건너뛴다.
 * <p>
 * 극단 열두달(대표 박정훈 소유자, 기획 단원 운영자, 이미 나간 단원)과 늦여름(이도현 소유자) — 둘 다 활동 중. 승인 · 초대 기능을 거치지 않고
 * 표에 바로 넣는다. 테스트끼리 같은 DB를 쓰므로 테스트마다 새 주최자와 운영진을 만들고, 기록은 그 주최자의 것만 본다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({StaffOperationTestController.class, StaffOperationTestService.class})
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("운영진 권한 확인과 운영진 조작 기록 (진짜 PostgreSQL)")
class StaffPermissionIntegrationTest {

    /** 소속을 시작한 일시 — 2026-10-01 10:00 (한국 시각). 나간 단원은 그 이튿날 나갔다. */
    private static final Instant JOINED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 1, 10, 0));

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GetStaffOperationHistoryUseCase getStaffOperationHistoryUseCase;

    private long twelveMonths;
    private long lateSummer;
    private long junghoon;
    private long planner;
    private long formerMember;
    private long dohyun;
    private String plannerNickname;

    @BeforeEach
    void storeOrganizers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        junghoon = staffAccount("박정훈", "고래" + suffix);
        plannerNickname = "바람" + suffix;
        planner = staffAccount("김기획", plannerNickname);
        formerMember = staffAccount("이단원", "단원" + suffix);
        dohyun = staffAccount("이도현", "도현" + suffix);

        twelveMonths = organizer("극단 열두달 " + suffix);
        membership(twelveMonths, junghoon, StaffRole.OWNER, null);
        membership(twelveMonths, planner, StaffRole.OPERATOR, null);
        membership(twelveMonths, formerMember, StaffRole.OPERATOR, JOINED_AT.plusSeconds(86_400));
        lateSummer = organizer("늦여름 " + suffix);
        membership(lateSummer, dohyun, StaffRole.OWNER, null);
    }

    @Test
    @DisplayName("소유자가 소유자만 하는 일(주최자명 변경)을 하면 되고, 주최자 · 운영진 · 소유자 역할 · 한 일 · 대상 · 요약 · 시각이 한 줄 남는다")
    void ownerPerformsOwnerOnlyActionAndIsRecorded() throws Exception {
        Instant before = Instant.now();
        operate(junghoon, twelveMonths, StaffAction.RENAME_ORGANIZER)
                .andExpect(status().isOk())
                .andExpect(content().string("OWNER"));
        Instant after = Instant.now();

        assertThat(recordsOf(twelveMonths)).singleElement().satisfies(record -> {
            assertThat(record.get("staff_account_id")).isEqualTo(junghoon);
            assertThat(record.get("staff_role")).isEqualTo("OWNER");
            assertThat(record.get("staff_action")).isEqualTo("RENAME_ORGANIZER");
            assertThat(record.get("target_kind")).isEqualTo("ORGANIZER");
            assertThat(record.get("target_id")).isEqualTo(twelveMonths);
            assertThat(record.get("summary")).isEqualTo("시험 조작 — RENAME_ORGANIZER");
            assertThat(((Timestamp) record.get("operated_at")).toInstant())
                    .isBetween(before.minusMillis(1), after.plusMillis(1));
        });
    }

    @Test
    @DisplayName("운영자는 운영진 공통의 일(공개 정보 편집)을 하고, 운영자 역할로 남는다")
    void operatorPerformsSharedActionAndIsRecorded() throws Exception {
        operate(planner, twelveMonths, StaffAction.EDIT_PUBLIC_PROFILE)
                .andExpect(status().isOk())
                .andExpect(content().string("OPERATOR"));

        assertThat(recordsOf(twelveMonths)).singleElement().satisfies(record -> {
            assertThat(record.get("staff_account_id")).isEqualTo(planner);
            assertThat(record.get("staff_role")).isEqualTo("OPERATOR");
            assertThat(record.get("staff_action")).isEqualTo("EDIT_PUBLIC_PROFILE");
        });
    }

    @Test
    @DisplayName("운영자가 소유자만 하는 일(회차 취소)을 하면 403 OWNER_ONLY_ACTION이고 기록이 남지 않는다")
    void operatorIsForbiddenOwnerOnlyAction() throws Exception {
        operate(planner, twelveMonths, StaffAction.CANCEL_SESSION)
                .andExpect(status().isForbidden())
                .andExpect(errorBody("OWNER_ONLY_ACTION", "소유자만 할 수 있는 일입니다."));

        assertThat(recordsOf(twelveMonths)).isEmpty();
    }

    @Test
    @DisplayName("다른 주최자의 소유자가 경로의 주최자 id만 바꿔 오면 403 NOT_ORGANIZER_STAFF이고 어느 주최자에도 기록이 남지 않는다")
    void otherOrganizerStaffIsForbidden() throws Exception {
        operate(dohyun, twelveMonths, StaffAction.VIEW_STATUS)
                .andExpect(status().isForbidden())
                .andExpect(errorBody("NOT_ORGANIZER_STAFF", "이 주최자의 운영진이 아닙니다."));
        operate(planner, lateSummer, StaffAction.EDIT_PUBLIC_PROFILE)
                .andExpect(status().isForbidden())
                .andExpect(errorBody("NOT_ORGANIZER_STAFF", "이 주최자의 운영진이 아닙니다."));

        assertThat(recordsOf(twelveMonths)).isEmpty();
        assertThat(recordsOf(lateSummer)).isEmpty();
    }

    @Test
    @DisplayName("이미 나간 운영자는 403 NOT_ORGANIZER_STAFF다 — 소속 이력은 남아도 지금 소속이 아니다")
    void formerMemberIsForbidden() throws Exception {
        operate(formerMember, twelveMonths, StaffAction.VIEW_SETTLEMENT)
                .andExpect(status().isForbidden())
                .andExpect(errorBody("NOT_ORGANIZER_STAFF", "이 주최자의 운영진이 아닙니다."));
    }

    @Test
    @DisplayName("없는 주최자도 403 NOT_ORGANIZER_STAFF로 같게 답한다")
    void missingOrganizerAnswersSameAsNotStaff() throws Exception {
        Long missing = jdbcTemplate.queryForObject("SELECT coalesce(max(id), 0) + 1000 FROM organizer", Long.class);
        assertThat(missing).isNotNull();

        operate(junghoon, missing, StaffAction.VIEW_STATUS)
                .andExpect(status().isForbidden())
                .andExpect(errorBody("NOT_ORGANIZER_STAFF", "이 주최자의 운영진이 아닙니다."));
    }

    @Test
    @DisplayName("기록을 남긴 뒤 조작이 실패하면 기록도 함께 되돌려진다 — 조작과 같은 트랜잭션")
    void recordRollsBackWithFailedOperation() throws Exception {
        mockMvc.perform(post("/api/organizer/organizers/" + twelveMonths + "/test-operations/RENAME_ORGANIZER")
                        .param("failAfterRecording", "true")
                        .with(csrf())
                        .with(loggedInAs(AccountKind.STAFF, junghoon)))
                .andExpect(status().isInternalServerError());

        assertThat(recordsOf(twelveMonths)).isEmpty();
    }

    @Test
    @DisplayName("주최자 이력에는 그 주최자의 기록이 최근 것부터 나오고, 사람은 지금 닉네임 · 이름과 그때의 역할로 나온다")
    void historyListsOrganizerRecordsNewestFirst() throws Exception {
        operate(junghoon, twelveMonths, StaffAction.RENAME_ORGANIZER).andExpect(status().isOk());
        operate(planner, twelveMonths, StaffAction.EDIT_PUBLIC_PROFILE).andExpect(status().isOk());
        operate(dohyun, lateSummer, StaffAction.EDIT_PUBLIC_PROFILE).andExpect(status().isOk());

        StaffOperationHistoryResult history =
                getStaffOperationHistoryUseCase.getHistory(new GetStaffOperationHistoryQuery(twelveMonths, null, 10));

        assertThat(history.entries()).extracting(StaffOperationHistoryResult.Entry::summary)
                .containsExactly("시험 조작 — EDIT_PUBLIC_PROFILE", "시험 조작 — RENAME_ORGANIZER");
        assertThat(history.entries().getFirst()).satisfies(entry -> {
            assertThat(entry.staffAccountId()).isEqualTo(planner);
            assertThat(entry.staffNickname()).isEqualTo(plannerNickname);
            assertThat(entry.staffName()).isEqualTo("김기획");
            assertThat(entry.staffRole()).isEqualTo(StaffRole.OPERATOR);
        });
        assertThat(history.nextBeforeRecordId()).isNull();
    }

    private ResultActions operate(long staffAccountId, long organizerId, StaffAction action) throws Exception {
        return mockMvc.perform(post("/api/organizer/organizers/" + organizerId + "/test-operations/" + action)
                .with(csrf())
                .with(loggedInAs(AccountKind.STAFF, staffAccountId)));
    }

    private List<Map<String, Object>> recordsOf(long organizerId) {
        return jdbcTemplate.queryForList("""
                SELECT staff_account_id, staff_role, staff_action, target_kind, target_id, summary, operated_at
                FROM staff_operation_record WHERE organizer_id = ? ORDER BY id""", organizerId);
    }

    /** 이메일 인증을 마친 운영진 로그인 한 줄. 이메일은 테스트마다 새로 만든다. */
    private long staffAccount(String name, String nickname) {
        String email = "staff-" + UUID.randomUUID() + "@example.com";
        Timestamp signedUpAt = timestamp(JOINED_AT.minusSeconds(86_400));
        Long id = jdbcTemplate.queryForObject("""
                INSERT INTO staff_account (status, name, nickname, email, email_lowercase, password_hash, signed_up_at,
                                           email_verified_at)
                VALUES ('ACTIVE', ?, ?, ?, lower(?), '{test}hashed-password', ?, ?)
                RETURNING id""", Long.class, name, nickname, email, email, signedUpAt, signedUpAt);
        assertThat(id).isNotNull();
        return id;
    }

    /** 활동 중인 주최자 한 줄 — 승인은 #23이 하지만 여기서는 표에 바로 넣는다. */
    private long organizer(String name) {
        Long id = jdbcTemplate.queryForObject("INSERT INTO organizer (name, status) VALUES (?, 'ACTIVE') RETURNING id",
                Long.class, name);
        assertThat(id).isNotNull();
        return id;
    }

    /**
     * 소속 한 줄 — {@link #JOINED_AT}부터.
     *
     * @param endedAt 나간 일시. 지금 소속이면 null.
     */
    private void membership(long organizerId, long staffAccountId, StaffRole role, Instant endedAt) {
        if (endedAt == null) {
            jdbcTemplate.update("""
                    INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at)
                    VALUES (?, ?, ?, ?)""", organizerId, staffAccountId, role.name(), timestamp(JOINED_AT));
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO staff_membership (organizer_id, staff_account_id, role, started_at, ended_at)
                VALUES (?, ?, ?, ?, ?)""", organizerId, staffAccountId, role.name(), timestamp(JOINED_AT),
                timestamp(endedAt));
    }

    private static Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
