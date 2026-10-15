package com.ticketon.application.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.ticketon.application.admin.port.in.RejectOrganizerApplicationCommand;
import com.ticketon.application.admin.port.in.RejectOrganizerApplicationResult;
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
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.ContactPoint;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerApplication;
import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.domain.organizer.RejectionReasonTemplate;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("주최자 신청 반려 유스케이스 (docs/01 \"반려 — 사유를 반드시 남기고 전달합니다\")")
class RejectOrganizerApplicationServiceTest {

    private static final Instant SUBMITTED_AT = KoreanTime.toInstant(LocalDateTime.of(2026, 10, 15, 10, 0));

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 15, 0);

    private static final long JUNGHOON = 1L;

    private final FakeAdminAccountRepository adminAccountRepository = new FakeAdminAccountRepository();
    private final FakeOrganizerApplicationRepository applicationRepository = new FakeOrganizerApplicationRepository();
    private final FakeOrganizerRepository organizerRepository = new FakeOrganizerRepository();
    private final FakeOrganizerStaffRepository staffRepository = new FakeOrganizerStaffRepository();
    private final RecordingAdminOperationRecordRepository recordRepository = new RecordingAdminOperationRecordRepository();
    private final RecordingSendNotificationUseCase sendNotificationUseCase = new RecordingSendNotificationUseCase();
    private final MutableClock clock = MutableClock.at(NOW);

    private final RejectOrganizerApplicationService service = new RejectOrganizerApplicationService(
            adminAccountRepository, applicationRepository, new AdminOperationRecorder(recordRepository),
            sendNotificationUseCase, clock);

    private long dohyun;
    private long organizerId;
    private long applicationId;

    @BeforeEach
    void storeApplicationUnderReview() {
        dohyun = adminAccountRepository.store("dohyun@ticket-on.example", FakePasswordHashPort.hashOf("admin-1234"),
                AdminGrade.ADMIN, SUBMITTED_AT);
        organizerId = organizerRepository.store("극단 열두달", OrganizerStatus.UNDER_REVIEW);
        applicationId = applicationRepository.store(organizerId, JUNGHOON, OrganizerApplicationForms.twelveMonths(),
                SUBMITTED_AT);
    }

    @Nested
    @DisplayName("반려한다")
    class Rejects {

        @ParameterizedTest(name = "{0}")
        @EnumSource(RejectionReasonTemplate.class)
        @DisplayName("템플릿 사유로 반려하면 신청서에 반려 상태 · 처리 일시 · 사유가 남는다")
        void rejectsWithTemplateReason(RejectionReasonTemplate template) {
            service.reject(command(template.reason()));

            OrganizerApplication saved = applicationRepository.findById(applicationId).orElseThrow();
            assertThat(saved.status()).isEqualTo(OrganizerApplicationStatus.REJECTED);
            assertThat(saved.reviewedAt()).contains(clock.instant());
            assertThat(saved.rejectionReason()).contains(template.reason());
        }

        @Test
        @DisplayName("직접 입력한 사유도 그대로 남고 전달된다")
        void rejectsWithCustomReason() {
            RejectionReason custom = RejectionReason.of("  공연 이력을 확인할 수 있는 링크를 함께 보내 주세요.  ");

            RejectOrganizerApplicationResult result = service.reject(command(custom));

            assertThat(applicationRepository.findById(applicationId).orElseThrow().rejectionReason())
                    .contains(RejectionReason.of("공연 이력을 확인할 수 있는 링크를 함께 보내 주세요."));
            assertThat(result).isEqualTo(new RejectOrganizerApplicationResult(applicationId, "극단 열두달",
                    "공연 이력을 확인할 수 있는 링크를 함께 보내 주세요.", clock.instant()));
        }

        @Test
        @DisplayName("심사받던 주최자는 심사 중으로 남고, 신청한 사람에게 소속이 생기지 않는다")
        void organizerStaysUnderReviewWithoutStaff() {
            service.reject(command(RejectionReasonTemplate.UNREACHABLE.reason()));

            assertThat(organizerRepository.statusOf(organizerId)).isEqualTo(OrganizerStatus.UNDER_REVIEW);
            assertThat(staffRepository.findByOrganizerId(organizerId).memberships()).isEmpty();
        }

        @Test
        @DisplayName("관리자 조작 기록이 사유와 함께 한 줄 남는다")
        void leavesOneOperationRecordWithReason() {
            service.reject(command(RejectionReasonTemplate.INSUFFICIENT_INFORMATION.reason()));

            assertThat(recordRepository.records()).singleElement().satisfies(record -> {
                assertThat(record.operator()).isEqualTo(AccountRef.of(AccountKind.ADMIN, dohyun));
                assertThat(record.operatedAt()).isEqualTo(clock.instant());
                assertThat(record.kind()).isEqualTo(AdminOperationKind.REJECT_ORGANIZER_APPLICATION);
                assertThat(record.targetKind()).isEqualTo(AdminOperationTargetKind.ORGANIZER_APPLICATION);
                assertThat(record.targetId()).isEqualTo(applicationId);
                assertThat(record.summary()).isEqualTo("주최자 신청 반려 — 극단 열두달");
                assertThat(record.reason()).hasValue("정보가 부족함");
            });
        }

        @Test
        @DisplayName("반려 메일은 사유를 담아 신청서의 통지 이메일로 간다 — 다시 신청할 수 있다고 알린다")
        void sendsRejectionMailWithReason() {
            service.reject(command(RejectionReasonTemplate.NOT_SUITABLE_FOR_SERVICE.reason()));

            assertThat(sendNotificationUseCase.sentCommands()).hasSize(1);
            SendNotificationCommand sent = sendNotificationUseCase.sentCommands().getFirst();
            assertThat(sent.kind()).isEqualTo(NotificationKind.ORGANIZER_APPLICATION_REJECTED);
            assertThat(sent.recipient()).isEqualTo(NotificationRecipient.contact(
                    ContactPoint.emailOnly(EmailAddress.of("twelve-months@example.com"))));
            assertThat(sent.content().title()).isEqualTo("주최자 신청이 반려되었습니다");
            assertThat(sent.content().body()).contains("'극단 열두달' 주최자 신청이 반려되었습니다.",
                    "반려 사유: 서비스 성격과 맞지 않음", "언제든 다시 신청할 수 있습니다");
        }
    }

    @Nested
    @DisplayName("반려하지 않는다 — 신청서·기록·메일 모두 그대로")
    class NotRejected {

        @Test
        @DisplayName("이미 반려된 신청서를 다시 반려하면 규칙 위반 APPLICATION_ALREADY_REVIEWED — 처음 사유가 그대로다")
        void alreadyReviewed() {
            applicationRepository.rejectInTable(applicationId, "연락이 닿지 않음", clock.instant());

            assertThatExceptionOfType(RuleViolationException.class)
                    .isThrownBy(() -> service.reject(command(RejectionReasonTemplate.INSUFFICIENT_INFORMATION.reason())))
                    .satisfies(exception -> assertThat(exception.errorCode())
                            .isEqualTo(OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED));
            assertThat(applicationRepository.findById(applicationId).orElseThrow().rejectionReason())
                    .contains(RejectionReason.of("연락이 닿지 않음"));
            assertThat(recordRepository.records()).isEmpty();
            assertThat(sendNotificationUseCase.sentCommands()).isEmpty();
        }

        @Test
        @DisplayName("다른 관리자가 거의 동시에 먼저 처리했으면 충돌 APPLICATION_ALREADY_REVIEWED — 기록도 메일도 없다")
        void anotherReviewWins() {
            applicationRepository.letAnotherReviewWinNextSave();

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> service.reject(command(RejectionReasonTemplate.UNREACHABLE.reason())))
                    .satisfies(exception -> assertThat(exception.errorCode())
                            .isEqualTo(OrganizerErrorCode.APPLICATION_ALREADY_REVIEWED));
            assertThat(applicationRepository.findById(applicationId).orElseThrow().status())
                    .isEqualTo(OrganizerApplicationStatus.UNDER_REVIEW);
            assertThat(recordRepository.records()).isEmpty();
            assertThat(sendNotificationUseCase.sentCommands()).isEmpty();
        }

        @Test
        @DisplayName("없는 신청서면 찾을 수 없음 APPLICATION_NOT_FOUND")
        void unknownApplication() {
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> service.reject(new RejectOrganizerApplicationCommand(
                            AccountRef.of(AccountKind.ADMIN, dohyun), applicationId + 100,
                            RejectionReasonTemplate.UNREACHABLE.reason())))
                    .satisfies(exception -> assertThat(exception.errorCode())
                            .isEqualTo(OrganizerErrorCode.APPLICATION_NOT_FOUND));
            assertThat(recordRepository.records()).isEmpty();
        }
    }

    @Nested
    @DisplayName("사유 없는 반려는 입력부터 만들어지지 않는다")
    class ReasonRequired {

        @Test
        @DisplayName("사유가 없으면 반려 입력을 만들 수 없다")
        void commandRequiresReason() {
            assertThatNullPointerException().isThrownBy(() -> new RejectOrganizerApplicationCommand(
                    AccountRef.of(AccountKind.ADMIN, dohyun), applicationId, null));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "   ", "　"})
        @DisplayName("공백뿐인 사유는 사유가 아니다")
        void blankReasonIsNotAReason(String blank) {
            assertThatIllegalArgumentException().isThrownBy(() -> RejectionReason.of(blank));
        }
    }

    private RejectOrganizerApplicationCommand command(RejectionReason reason) {
        return new RejectOrganizerApplicationCommand(AccountRef.of(AccountKind.ADMIN, dohyun), applicationId, reason);
    }
}
