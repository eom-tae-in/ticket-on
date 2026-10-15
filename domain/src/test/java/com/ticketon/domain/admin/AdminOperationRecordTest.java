package com.ticketon.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.organizer.OrganizerName;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.domain.organizer.RejectionReasonTemplate;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("관리자 조작 기록 — 누가 언제 무엇을 (docs/01 \"관리자 2등급\")")
class AdminOperationRecordTest {

    private static final Instant OPERATED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final AccountRef SUPER_ADMIN = AccountRef.of(AccountKind.ADMIN, 1);

    @Nested
    @DisplayName("관리자 계정 발급 기록")
    class AdminAccountIssued {

        @Test
        @DisplayName("누가(발급한 최고 관리자) · 언제 · 무엇을(관리자 계정 발급) · 대상(발급한 관리자 계정 id) · 요약을 남기고, 사유는 없다")
        void recordsIssuance() {
            AdminOperationRecord record = AdminOperationRecord.adminAccountIssued(SUPER_ADMIN, 2,
                    EmailAddress.of("new-admin@ticket-on.example"), AdminGrade.ADMIN, OPERATED_AT);

            assertThat(record.operator()).isEqualTo(SUPER_ADMIN);
            assertThat(record.adminAccountId()).isEqualTo(1);
            assertThat(record.operatedAt()).isEqualTo(OPERATED_AT);
            assertThat(record.kind()).isEqualTo(AdminOperationKind.ISSUE_ADMIN_ACCOUNT);
            assertThat(record.targetKind()).isEqualTo(AdminOperationTargetKind.ADMIN_ACCOUNT);
            assertThat(record.targetId()).isEqualTo(2);
            assertThat(record.summary()).isEqualTo("관리자 계정 발급 — new-admin@ticket-on.example (관리자)");
            assertThat(record.reason()).isEmpty();
        }

        @Test
        @DisplayName("최고 관리자를 발급하면 요약에 '최고 관리자'가 적힌다")
        void summaryShowsSuperAdminGrade() {
            AdminOperationRecord record = AdminOperationRecord.adminAccountIssued(SUPER_ADMIN, 3,
                    EmailAddress.of("second-super@ticket-on.example"), AdminGrade.SUPER_ADMIN, OPERATED_AT);

            assertThat(record.summary()).isEqualTo("관리자 계정 발급 — second-super@ticket-on.example (최고 관리자)");
        }
    }

    @Nested
    @DisplayName("주최자 신청 심사 기록")
    class OrganizerApplicationReviewed {

        private static final AccountRef DOHYUN = AccountRef.of(AccountKind.ADMIN, 4);

        @Test
        @DisplayName("승인은 누가(관리자) · 언제 · 무엇을(주최자 신청 승인) · 대상(신청서 id) · 요약(신청한 이름)을 남기고, 사유는 없다")
        void recordsApproval() {
            AdminOperationRecord record = AdminOperationRecord.organizerApplicationApproved(DOHYUN, 3,
                    OrganizerName.of("극단 열두달"), OPERATED_AT);

            assertThat(record.operator()).isEqualTo(DOHYUN);
            assertThat(record.operatedAt()).isEqualTo(OPERATED_AT);
            assertThat(record.kind()).isEqualTo(AdminOperationKind.APPROVE_ORGANIZER_APPLICATION);
            assertThat(record.targetKind()).isEqualTo(AdminOperationTargetKind.ORGANIZER_APPLICATION);
            assertThat(record.targetId()).isEqualTo(3);
            assertThat(record.summary()).isEqualTo("주최자 신청 승인 — 극단 열두달");
            assertThat(record.reason()).isEmpty();
        }

        @Test
        @DisplayName("반려는 신청한 사람에게 전달한 사유를 그대로 사유로 남긴다 — 템플릿이든 직접 입력이든")
        void recordsRejectionWithReason() {
            AdminOperationRecord template = AdminOperationRecord.organizerApplicationRejected(DOHYUN, 3,
                    OrganizerName.of("극단 열두달"), RejectionReasonTemplate.UNREACHABLE.reason(), OPERATED_AT);
            AdminOperationRecord custom = AdminOperationRecord.organizerApplicationRejected(DOHYUN, 5,
                    OrganizerName.of("늦여름"), RejectionReason.of("사업자등록번호가 확인되지 않습니다."), OPERATED_AT);

            assertThat(template.kind()).isEqualTo(AdminOperationKind.REJECT_ORGANIZER_APPLICATION);
            assertThat(template.targetKind()).isEqualTo(AdminOperationTargetKind.ORGANIZER_APPLICATION);
            assertThat(template.targetId()).isEqualTo(3);
            assertThat(template.summary()).isEqualTo("주최자 신청 반려 — 극단 열두달");
            assertThat(template.reason()).hasValue("연락이 닿지 않음");
            assertThat(custom.reason()).hasValue("사업자등록번호가 확인되지 않습니다.");
        }
    }

    @Nested
    @DisplayName("한 줄의 규칙")
    class Rules {

        @Test
        @DisplayName("요약과 사유는 앞뒤 공백을 걷는다")
        void stripsSummaryAndReason() {
            AdminOperationRecord record = AdminOperationRecord.of(SUPER_ADMIN, AdminOperationKind.ISSUE_ADMIN_ACCOUNT, 2,
                    "  요약  ", "　사유　", OPERATED_AT);

            assertThat(record.summary()).isEqualTo("요약");
            assertThat(record.reason()).hasValue("사유");
        }

        @Test
        @DisplayName("대상 종류는 조작 종류가 정한다 — 관리자 계정 발급의 대상은 관리자 계정, 주최자 신청 심사의 대상은 신청서다")
        void targetKindFollowsOperationKind() {
            assertThat(AdminOperationKind.ISSUE_ADMIN_ACCOUNT.targetKind()).isEqualTo(AdminOperationTargetKind.ADMIN_ACCOUNT);
            assertThat(AdminOperationKind.APPROVE_ORGANIZER_APPLICATION.targetKind())
                    .isEqualTo(AdminOperationTargetKind.ORGANIZER_APPLICATION);
            assertThat(AdminOperationKind.REJECT_ORGANIZER_APPLICATION.targetKind())
                    .isEqualTo(AdminOperationTargetKind.ORGANIZER_APPLICATION);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AccountKind.class, names = "ADMIN", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("관리자가 아닌 주체의 조작은 기록하지 않는다")
        void rejectsNonAdminOperator(AccountKind kind) {
            assertThatIllegalArgumentException().isThrownBy(() -> AdminOperationRecord.of(AccountRef.of(kind, 1),
                    AdminOperationKind.ISSUE_ADMIN_ACCOUNT, 2, "요약", null, OPERATED_AT));
        }

        @Test
        @DisplayName("대상 id는 1 이상이어야 한다")
        void rejectsNonPositiveTargetId() {
            assertThatIllegalArgumentException().isThrownBy(() -> AdminOperationRecord.of(SUPER_ADMIN,
                    AdminOperationKind.ISSUE_ADMIN_ACCOUNT, 0, "요약", null, OPERATED_AT));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "   ", "　"})
        @DisplayName("요약이 비면 안 된다")
        void rejectsBlankSummary(String summary) {
            assertThatIllegalArgumentException().isThrownBy(() -> AdminOperationRecord.of(SUPER_ADMIN,
                    AdminOperationKind.ISSUE_ADMIN_ACCOUNT, 2, summary, null, OPERATED_AT));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "   ", "　"})
        @DisplayName("사유가 없는 조작은 null로 넘긴다 — 공백뿐인 사유는 받지 않는다")
        void rejectsBlankReason(String reason) {
            assertThatIllegalArgumentException().isThrownBy(() -> AdminOperationRecord.of(SUPER_ADMIN,
                    AdminOperationKind.ISSUE_ADMIN_ACCOUNT, 2, "요약", reason, OPERATED_AT));
        }
    }
}
