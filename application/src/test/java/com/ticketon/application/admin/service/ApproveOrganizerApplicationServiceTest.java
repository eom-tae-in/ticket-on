package com.ticketon.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.application.admin.port.in.ApproveOrganizerApplicationCommand;
import com.ticketon.application.admin.port.in.ApproveOrganizerApplicationResult;
import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.common.KoreanTime;
import com.ticketon.common.MutableClock;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.admin.AdminOperationKind;
import com.ticketon.domain.admin.AdminOperationTargetKind;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.ContactPoint;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("주최자 신청 승인 유스케이스 (docs/01 \"승인되면 신청한 사람이 소유자가 되고 행사 개설 가능\")")
class ApproveOrganizerApplicationServiceTest {

    private static final Instant SUBMITTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 15, 0);

    private static final long JUNGHOON = 1L;

    private final FakeAdminAccountRepository adminAccountRepository = new FakeAdminAccountRepository();
    private final FakeStaffAccountRepository staffAccountRepository = new FakeStaffAccountRepository();
    private final FakeOrganizerApplicationRepository applicationRepository = new FakeOrganizerApplicationRepository();
    private final FakeOrganizerRepository organizerRepository = new FakeOrganizerRepository();
    private final FakeOrganizerStaffRepository staffRepository = new FakeOrganizerStaffRepository();
    private final RecordingAdminOperationRecordRepository recordRepository = new RecordingAdminOperationRecordRepository();
    private final RecordingSendNotificationUseCase sendNotificationUseCase = new RecordingSendNotificationUseCase();
    private final MutableClock clock = MutableClock.at(NOW);

    private final ApproveOrganizerApplicationService service = new ApproveOrganizerApplicationService(
            adminAccountRepository, staffAccountRepository, applicationRepository, organizerRepository, staffRepository,
            new AdminOperationRecorder(recordRepository), sendNotificationUseCase, clock);

    private long dohyun;
    private long organizerId;
    private long applicationId;

    @BeforeEach
    void storeApplicationUnderReview() {
        dohyun = storedAdmin("dohyun@ticket-on.example", AdminGrade.ADMIN);
        staffAccountRepository.storeActive(JUNGHOON, "박정훈", "조용한고래", "junghoon@example.com");
        organizerId = organizerRepository.store("극단 열두달", OrganizerStatus.UNDER_REVIEW);
        applicationId = applicationRepository.store(organizerId, JUNGHOON, OrganizerApplicationForms.twelveMonths(),
                SUBMITTED_AT);
    }

    @Nested
    @DisplayName("승인한다")
    class Approves {

        @Test
        @DisplayName("신청서는 승인 상태와 처리 일시가 남고, 주최자는 활동이 된다")
        void approvesApplicationAndActivatesOrganizer() {
            service.approve(command(dohyun));

            OrganizerApplication saved = applicationRepository.findById(applicationId).orElseThrow();
            assertThat(saved.status()).isEqualTo(OrganizerApplicationStatus.APPROVED);
            assertThat(saved.reviewedAt()).contains(clock.instant());
            assertThat(saved.rejectionReason()).isEmpty();
            assertThat(organizerRepository.statusOf(organizerId)).isEqualTo(OrganizerStatus.ACTIVE);
        }

        @Test
        @DisplayName("신청한 사람의 운영진 계정 줄을 잠그고 읽는다 — 같은 사람의 탈퇴와 엇갈리지 않게(계정 줄 → 소속 줄)")
        void locksApplicantAccount() {
            service.approve(command(dohyun));

            assertThat(staffAccountRepository.lockedAccountIds()).containsExactly(JUNGHOON);
        }

        @Test
        @DisplayName("신청한 사람이 승인 일시부터 그 주최자의 소유자가 된다")
        void applicantBecomesOwner() {
            service.approve(command(dohyun));

            assertThat(staffRepository.findByOrganizerId(organizerId).memberships()).containsExactly(
                    new StaffMembership(organizerId, JUNGHOON, StaffRole.OWNER, clock.instant(), null));
        }

        @Test
        @DisplayName("관리자 조작 기록이 한 줄 남는다 — 누가 · 언제 · 주최자 신청 승인 · 대상 신청서 · 요약, 사유 없음")
        void leavesOneOperationRecord() {
            service.approve(command(dohyun));

            assertThat(recordRepository.records()).singleElement().satisfies(record -> {
                assertThat(record.operator()).isEqualTo(AccountRef.of(AccountKind.ADMIN, dohyun));
                assertThat(record.operatedAt()).isEqualTo(clock.instant());
                assertThat(record.kind()).isEqualTo(AdminOperationKind.APPROVE_ORGANIZER_APPLICATION);
                assertThat(record.targetKind()).isEqualTo(AdminOperationTargetKind.ORGANIZER_APPLICATION);
                assertThat(record.targetId()).isEqualTo(applicationId);
                assertThat(record.summary()).isEqualTo("주최자 신청 승인 — 극단 열두달");
                assertThat(record.reason()).isEmpty();
            });
        }

        @Test
        @DisplayName("승인 메일은 신청서의 통지 이메일로 간다 — 주최자 id가 아니라 그 사람의 연락처로(INDIVIDUAL)")
        void sendsApprovalMailToApplicationEmail() {
            service.approve(command(dohyun));

            SendNotificationCommand sent = sendNotificationUseCase.sentCommands().getFirst();
            assertThat(sendNotificationUseCase.sentCommands()).hasSize(1);
            assertThat(sent.kind()).isEqualTo(NotificationKind.ORGANIZER_APPLICATION_APPROVED);
            assertThat(sent.recipient()).isEqualTo(NotificationRecipient.contact(
                    ContactPoint.emailOnly(EmailAddress.of("twelve-months@example.com"))));
            assertThat(sent.content().title()).isEqualTo("주최자 신청이 승인되었습니다");
            assertThat(sent.content().body()).contains("'극단 열두달' 주최자 신청이 승인되었습니다.", "이제 행사를 열 수 있습니다");
        }

        @Test
        @DisplayName("결과에 신청서 · 주최자 · 주최자명 · 소유자 · 승인 일시가 온다")
        void returnsApprovedResult() {
            ApproveOrganizerApplicationResult result = service.approve(command(dohyun));

            assertThat(result).isEqualTo(new ApproveOrganizerApplicationResult(applicationId, organizerId, "극단 열두달",
                    JUNGHOON, clock.instant()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(AdminGrade.class)
        @DisplayName("관리자와 최고 관리자 모두 승인한다 (docs/01 \"관리자 2등급\")")
        void bothGradesApprove(AdminGrade grade) {
            long admin = storedAdmin(grade.name().toLowerCase(Locale.ROOT) + "@ticket-on.example", grade);

            service.approve(command(admin));

            assertThat(recordRepository.records()).singleElement()
                    .satisfies(record -> assertThat(record.adminAccountId()).isEqualTo(admin));
        }
    }

    @Nested
    @DisplayName("승인하지 않는다 — 신청서·주최자·소속·기록·메일 모두 그대로")
    class Rejected {

        @Test
        @DisplayName("없는 신청서면 찾을 수 없음 APPLICATION_NOT_FOUND")
        void unknownApplication() {
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> service.approve(new ApproveOrganizerApplicationCommand(
                            AccountRef.of(AccountKind.ADMIN, dohyun), applicationId + 100)))
                    .satisfies(exception -> assertThat(exception.errorCode())
                            .isEqualTo(OrganizerErrorCode.APPLICATION_NOT_FOUND));
            assertNothingChanged();
        }

        @Test
        @DisplayName("이미 반려된 신청서면 규칙 위반 APPLICATION_ALREADY_REVIEWED")
        void alreadyRejected() {
            applicationRepository.rejectInTable(applicationId, "연락이 닿지 않음", clock.instant());

            assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED);
            assertThat(organizerRepository.statusOf(organizerId)).isEqualTo(OrganizerStatus.UNDER_REVIEW);
            assertThat(staffRepository.findByOrganizerId(organizerId).memberships()).isEmpty();
            assertThat(recordRepository.records()).isEmpty();
            assertThat(sendNotificationUseCase.sentCommands()).isEmpty();
        }

        @Test
        @DisplayName("신청한 운영진 로그인이 탈퇴했으면 규칙 위반 APPLICANT_WITHDRAWN — 탈퇴한 계정이 소유자가 되지 않는다")
        void applicantWithdrawn() {
            staffAccountRepository.markWithdrawn(JUNGHOON);

            assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.APPLICANT_WITHDRAWN);
            assertNothingChanged();
        }

        @Test
        @DisplayName("다른 관리자가 거의 동시에 먼저 처리했으면 충돌 APPLICATION_ALREADY_REVIEWED — 기록도 메일도 없다")
        void anotherReviewWins() {
            applicationRepository.letAnotherReviewWinNextSave();

            assertRejectedWith(ConflictException.class, OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED);
            assertNothingChanged();
        }

        @Test
        @DisplayName("심사받는 주최자가 이미 심사 중이 아니면 규칙 위반 ORGANIZER_NOT_UNDER_REVIEW")
        void organizerNoLongerUnderReview() {
            long activeOrganizer = organizerRepository.store("극단 열두달", OrganizerStatus.ACTIVE);
            long application = applicationRepository.store(activeOrganizer, JUNGHOON,
                    OrganizerApplicationForms.twelveMonths(), SUBMITTED_AT);

            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> service.approve(new ApproveOrganizerApplicationCommand(
                            AccountRef.of(AccountKind.ADMIN, dohyun), application)))
                    .satisfies(exception -> assertThat(exception.errorCode())
                            .isEqualTo(OrganizerErrorCode.ORGANIZER_NOT_UNDER_REVIEW));
            assertThat(applicationRepository.findById(application).orElseThrow().status())
                    .isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW);
            assertThat(recordRepository.records()).isEmpty();
            assertThat(sendNotificationUseCase.sentCommands()).isEmpty();
        }

        private void assertRejectedWith(Class<? extends DomainException> type, OrganizerErrorCode expectedCode) {
            assertThatExceptionOfType(type)
                    .isThrownBy(() -> service.approve(command(dohyun)))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
        }

        private void assertNothingChanged() {
            assertThat(applicationRepository.findById(applicationId).orElseThrow().status())
                    .isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW);
            assertThat(organizerRepository.statusOf(organizerId)).isEqualTo(OrganizerStatus.UNDER_REVIEW);
            assertThat(staffRepository.findByOrganizerId(organizerId).memberships()).isEmpty();
            assertThat(recordRepository.records()).isEmpty();
            assertThat(sendNotificationUseCase.sentCommands()).isEmpty();
        }
    }

    @Test
    @DisplayName("승인 입력은 관리자 주체만 받는다")
    void commandRequiresAdmin() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new ApproveOrganizerApplicationCommand(AccountRef.of(AccountKind.STAFF, JUNGHOON), applicationId));
    }

    private ApproveOrganizerApplicationCommand command(long adminAccountId) {
        return new ApproveOrganizerApplicationCommand(AccountRef.of(AccountKind.ADMIN, adminAccountId), applicationId);
    }

    private long storedAdmin(String email, AdminGrade grade) {
        return adminAccountRepository.store(email, FakePasswordHashPort.hashOf("admin-1234"), grade, SUBMITTED_AT);
    }
}
