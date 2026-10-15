package com.ticketon.application.organizer.service;

import static com.ticketon.application.organizer.service.OrganizerStaffScene.APPROVED_AT;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN_EMAIL;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.JUNGHOON;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.NOW;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.YEOREUM;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.acting;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.organizer.port.in.AcceptStaffInvitationResult;
import com.ticketon.application.organizer.port.in.InviteOperatorCommand;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffInvitation;
import com.ticketon.domain.organizer.StaffInvitationStatus;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 박정훈(극단 열두달 소유자)이 보낸 초대를 이도현(늦여름 운영자)이 받는다({@link OrganizerStaffScene}). */
@DisplayName("운영자 초대 수락 유스케이스")
class AcceptStaffInvitationServiceTest {

    private final OrganizerStaffScene scene = new OrganizerStaffScene();
    private final AcceptStaffInvitationService service = scene.acceptStaffInvitationService();

    private long invitationId;

    @BeforeEach
    void inviteDohyun() {
        invitationId = scene.inviteOperatorService()
                .invite(new InviteOperatorCommand(acting(JUNGHOON, scene.twelveMonths), EmailAddress.of(DOHYUN_EMAIL)))
                .invitationId();
    }

    private void assertRejectedWith(Class<? extends DomainException> type, OrganizerErrorCode expectedCode,
            ThrowingCallable action) {
        assertThatExceptionOfType(type)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    @Test
    @DisplayName("초대받은 이메일의 운영진 로그인으로 수락하면 바로 운영자가 되고, 운영자로서 조작 기록이 남는다 — 늦여름 소속은 그대로다")
    void acceptingMakesOperator() {
        Instant acceptedAt = NOW.plus(Duration.ofHours(30));
        scene.clock.moveTo(acceptedAt);

        AcceptStaffInvitationResult result = service.accept(answer(DOHYUN, invitationId));

        assertThat(result).isEqualTo(new AcceptStaffInvitationResult(scene.twelveMonths, "극단 열두달", acceptedAt));
        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).roleOf(DOHYUN)).contains(StaffRole.OPERATOR);
        assertThat(scene.staffRepository.findByOrganizerId(scene.lateSummer).roleOf(DOHYUN)).contains(StaffRole.OPERATOR);
        assertThat(scene.invitationRepository.findById(invitationId)).hasValueSatisfying(invitation -> {
            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.ACCEPTED);
            assertThat(invitation.answeredAt()).contains(acceptedAt);
            assertThat(invitation.answeredByStaffAccountId()).hasValue(DOHYUN);
        });
        assertThat(scene.recordRepository.records()).last().satisfies(record -> {
            assertThat(record.organizerId()).isEqualTo(scene.twelveMonths);
            assertThat(record.staffAccountId()).isEqualTo(DOHYUN);
            assertThat(record.staffRole()).isEqualTo(StaffRole.OPERATOR);
            assertThat(record.action()).isEqualTo(StaffAction.ACCEPT_STAFF_INVITATION);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.STAFF_INVITATION);
            assertThat(record.targetId()).isEqualTo(invitationId);
            assertThat(record.summary()).isEqualTo("운영자 초대 수락");
            assertThat(record.operatedAt()).isEqualTo(acceptedAt);
        });
    }

    @Test
    @DisplayName("초대 줄을 잠그고 읽는다 — 같은 초대를 동시에 수락한 늦은 쪽은 먼저 수락한 결과를 보고 409 STAFF_INVITATION_ALREADY_ANSWERED다")
    void readsInvitationWithLock() {
        service.accept(answer(DOHYUN, invitationId));

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED,
                () -> service.accept(answer(DOHYUN, invitationId)));
        assertThat(scene.invitationRepository.lockedReads()).containsExactly(invitationId, invitationId);
    }

    @Test
    @DisplayName("다른 이메일의 운영진 로그인으로 수락하면 404 STAFF_INVITATION_NOT_FOUND이고 아무것도 바뀌지 않는다 — 없는 초대와 같은 답")
    void anotherEmailCannotAccept() {
        assertRejectedWith(NotFoundException.class, OrganizerErrorCode.STAFF_INVITATION_NOT_FOUND,
                () -> service.accept(answer(YEOREUM, invitationId)));
        assertRejectedWith(NotFoundException.class, OrganizerErrorCode.STAFF_INVITATION_NOT_FOUND,
                () -> service.accept(answer(DOHYUN, invitationId + 100)));

        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).roleOf(YEOREUM)).isEmpty();
        assertThat(scene.invitationRepository.findById(invitationId).orElseThrow().status())
                .isEqualTo(StaffInvitationStatus.PENDING);
        assertThat(scene.recordRepository.records()).hasSize(1);
    }

    @Test
    @DisplayName("유효 기간(7일)이 지나면 409 STAFF_INVITATION_EXPIRED이고 운영자가 되지 않는다")
    void rejectsExpiredInvitation() {
        scene.clock.advance(Duration.ofDays(7));

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.STAFF_INVITATION_EXPIRED,
                () -> service.accept(answer(DOHYUN, invitationId)));

        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).roleOf(DOHYUN)).isEmpty();
    }

    @Test
    @DisplayName("이미 수락했거나 거절한 초대는 409 STAFF_INVITATION_ALREADY_ANSWERED다")
    void rejectsAnsweredInvitation() {
        service.accept(answer(DOHYUN, invitationId));

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED,
                () -> service.accept(answer(DOHYUN, invitationId)));
        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).activeMemberships()).hasSize(3);
    }

    @Test
    @DisplayName("초대한 주최자가 활동 중이 아니게 됐으면(닫힘) 409 ORGANIZER_NOT_ACTIVE다")
    void rejectsInactiveOrganizer() {
        StaffInvitation closedInvitation = scene.invitationRepository.add(StaffInvitation.issue(
                OrganizerStaff.startWithOwner(scene.closedSpring, JUNGHOON, APPROVED_AT), JUNGHOON,
                EmailAddress.of(DOHYUN_EMAIL), OptionalLong.of(DOHYUN), NOW));

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.ORGANIZER_NOT_ACTIVE,
                () -> service.accept(answer(DOHYUN, closedInvitation.id().orElseThrow())));
    }
}
