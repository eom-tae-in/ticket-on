package com.ticketon.application.organizer.service;

import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN_EMAIL;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.JUNGHOON;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.NOW;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.PLANNER;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.YEOREUM;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.acting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.application.organizer.port.in.InviteOperatorCommand;
import com.ticketon.application.organizer.port.in.InviteOperatorResult;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.ContactPoint;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffInvitation;
import com.ticketon.domain.organizer.StaffInvitationStatus;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Duration;
import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 극단 열두달의 대표 박정훈이 이도현을 운영자로 초대한다({@link OrganizerStaffScene}). */
@DisplayName("운영자 초대 유스케이스")
class InviteOperatorServiceTest {

    private final OrganizerStaffScene scene = new OrganizerStaffScene();
    private final InviteOperatorService service = scene.inviteOperatorService();

    private InviteOperatorResult invite(long staffAccountId, long organizerId, String email) {
        return service.invite(new InviteOperatorCommand(acting(staffAccountId, organizerId), EmailAddress.of(email)));
    }

    private void assertRejectedWith(Class<? extends DomainException> type, OrganizerErrorCode expectedCode,
            ThrowingCallable action) {
        assertThatExceptionOfType(type)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    @Test
    @DisplayName("소유자가 초대하면 7일 동안 답을 기다리는 초대가 저장되고, 소유자 이름으로 조작 기록이 남고, 받는 이메일로 초대 메일이 간다")
    void ownerInvites() {
        InviteOperatorResult result = invite(JUNGHOON, scene.twelveMonths, DOHYUN_EMAIL);

        assertThat(result.inviteeEmail()).isEqualTo(DOHYUN_EMAIL);
        assertThat(result.invitedAt()).isEqualTo(NOW);
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(scene.invitationRepository.savedInvitations()).singleElement().satisfies(invitation -> {
            assertThat(invitation.id()).hasValue(result.invitationId());
            assertThat(invitation.organizerId()).isEqualTo(scene.twelveMonths);
            assertThat(invitation.invitedByStaffAccountId()).isEqualTo(JUNGHOON);
            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.PENDING);
        });
        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.organizerId()).isEqualTo(scene.twelveMonths);
            assertThat(record.staffAccountId()).isEqualTo(JUNGHOON);
            assertThat(record.staffRole()).isEqualTo(StaffRole.OWNER);
            assertThat(record.action()).isEqualTo(StaffAction.INVITE_STAFF);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.STAFF_INVITATION);
            assertThat(record.targetId()).isEqualTo(result.invitationId());
            assertThat(record.summary()).isEqualTo("운영자 초대 · dohyun@example.com");
            assertThat(record.operatedAt()).isEqualTo(NOW);
        });
        SendNotificationCommand sent = scene.sendNotificationUseCase.sentCommands().getFirst();
        assertThat(scene.sendNotificationUseCase.sentCommands()).hasSize(1);
        assertThat(sent.kind()).isEqualTo(NotificationKind.OPERATOR_INVITED);
        assertThat(sent.recipient()).isEqualTo(NotificationRecipient.contact(
                ContactPoint.emailOnly(EmailAddress.of(DOHYUN_EMAIL))));
        assertThat(sent.content().title()).isEqualTo("'극단 열두달'에서 운영자로 초대했습니다");
        assertThat(sent.content().body()).contains("'극단 열두달'의 소유자가 이 이메일(dohyun@example.com)로 운영자 초대를 보냈습니다.",
                "https://ticket-on.example/organizer", "초대는 7일 동안 수락할 수 있습니다.");
    }

    @Test
    @DisplayName("아직 주최자 쪽 로그인이 없는 이메일도 초대한다")
    void invitesEmailWithoutStaffLogin() {
        invite(JUNGHOON, scene.twelveMonths, "new-member@example.com");

        assertThat(scene.invitationRepository.savedInvitations()).singleElement()
                .satisfies(invitation -> assertThat(invitation.inviteeEmail().value()).isEqualTo("new-member@example.com"));
    }

    @Test
    @DisplayName("다른 주최자(늦여름)의 운영자인 이도현도 초대한다 — 한 사람이 여러 주최자에 소속될 수 있다")
    void invitesStaffOfAnotherOrganizer() {
        invite(JUNGHOON, scene.twelveMonths, DOHYUN_EMAIL);

        assertThat(scene.invitationRepository.savedInvitations()).hasSize(1);
    }

    @Test
    @DisplayName("운영자가 초대하면 403 OWNER_ONLY_ACTION이고 초대 · 기록 · 메일이 남지 않는다")
    void operatorCannotInvite() {
        assertRejectedWith(PermissionDeniedException.class, OrganizerErrorCode.OWNER_ONLY_ACTION,
                () -> invite(PLANNER, scene.twelveMonths, DOHYUN_EMAIL));

        assertNothingHappened();
    }

    @Test
    @DisplayName("다른 주최자의 소유자가 경로의 주최자 id만 바꿔 초대하면 403 NOT_ORGANIZER_STAFF다")
    void otherOrganizerOwnerCannotInvite() {
        assertRejectedWith(PermissionDeniedException.class, OrganizerErrorCode.NOT_ORGANIZER_STAFF,
                () -> invite(YEOREUM, scene.twelveMonths, DOHYUN_EMAIL));

        assertNothingHappened();
    }

    @Test
    @DisplayName("이미 그 주최자의 운영진인 사람의 이메일(대소문자만 달라도)은 409 ALREADY_ORGANIZER_STAFF다")
    void rejectsCurrentStaffEmail() {
        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.ALREADY_ORGANIZER_STAFF,
                () -> invite(JUNGHOON, scene.twelveMonths, "PLANNER@example.com"));

        assertNothingHappened();
    }

    @Test
    @DisplayName("같은 이메일로 보낸 초대가 아직 유효하면 409 STAFF_INVITATION_ALREADY_PENDING이고 초대는 하나 그대로다")
    void rejectsDuplicatePendingInvitation() {
        invite(JUNGHOON, scene.twelveMonths, DOHYUN_EMAIL);
        scene.clock.advance(Duration.ofDays(7).minusSeconds(1));

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.STAFF_INVITATION_ALREADY_PENDING,
                () -> invite(JUNGHOON, scene.twelveMonths, "DoHyun@Example.com"));

        assertThat(scene.invitationRepository.savedInvitations()).singleElement()
                .satisfies(invitation -> assertThat(invitation.invitedAt()).isEqualTo(NOW));
        assertThat(scene.recordRepository.records()).hasSize(1);
        assertThat(scene.sendNotificationUseCase.sentCommands()).hasSize(1);
    }

    @Test
    @DisplayName("유효 기간이 지난 초대는 같은 이메일로 다시 초대하면 같은 초대가 지금부터 7일 동안 다시 열리고, 기록과 메일이 한 번 더 남는다")
    void reopensExpiredInvitation() {
        long invitationId = invite(JUNGHOON, scene.twelveMonths, DOHYUN_EMAIL).invitationId();
        scene.clock.advance(Duration.ofDays(10));
        Instant reinvitedAt = NOW.plus(Duration.ofDays(10));

        InviteOperatorResult result = invite(JUNGHOON, scene.twelveMonths, DOHYUN_EMAIL);

        assertThat(result.invitationId()).isEqualTo(invitationId);
        assertThat(result.invitedAt()).isEqualTo(reinvitedAt);
        assertThat(result.expiresAt()).isEqualTo(reinvitedAt.plus(Duration.ofDays(7)));
        assertThat(scene.invitationRepository.savedInvitations()).singleElement()
                .extracting(StaffInvitation::invitedAt).isEqualTo(reinvitedAt);
        assertThat(scene.recordRepository.records()).hasSize(2);
        assertThat(scene.sendNotificationUseCase.sentCommands()).hasSize(2);
    }

    @Test
    @DisplayName("다른 주최자는 같은 이메일로 따로 초대한다")
    void otherOrganizerInvitesSameEmailSeparately() {
        invite(JUNGHOON, scene.twelveMonths, "new-member@example.com");

        invite(YEOREUM, scene.lateSummer, "new-member@example.com");

        assertThat(scene.invitationRepository.savedInvitations()).extracting(StaffInvitation::organizerId)
                .containsExactly(scene.twelveMonths, scene.lateSummer);
    }

    private void assertNothingHappened() {
        assertThat(scene.invitationRepository.savedInvitations()).isEmpty();
        assertThat(scene.recordRepository.records()).isEmpty();
        assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
    }
}
