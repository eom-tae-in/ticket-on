package com.ticketon.application.organizer.service;

import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN_EMAIL;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.JUNGHOON;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.NOW;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.YEOREUM;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.acting;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.answer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.organizer.port.in.InviteOperatorCommand;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffInvitationStatus;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 박정훈(극단 열두달 소유자)이 보낸 초대를 이도현이 거절한다({@link OrganizerStaffScene}). */
@DisplayName("운영자 초대 거절 유스케이스")
class DeclineStaffInvitationServiceTest {

    private final OrganizerStaffScene scene = new OrganizerStaffScene();
    private final DeclineStaffInvitationService service = scene.declineStaffInvitationService();

    private long invitationId;

    @BeforeEach
    void inviteDohyun() {
        invitationId = invite();
    }

    private long invite() {
        return scene.inviteOperatorService()
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
    @DisplayName("거절하면 거절로 남고 명단은 그대로다 — 운영진이 아닌 사람의 일이라 조작 기록은 더 남지 않는다")
    void decliningLeavesStaffUnchanged() {
        scene.clock.advance(Duration.ofHours(1));

        service.decline(answer(DOHYUN, invitationId));

        assertThat(scene.invitationRepository.findById(invitationId)).hasValueSatisfying(invitation -> {
            assertThat(invitation.status()).isEqualTo(StaffInvitationStatus.DECLINED);
            assertThat(invitation.answeredAt()).contains(NOW.plus(Duration.ofHours(1)));
            assertThat(invitation.answeredByStaffAccountId()).hasValue(DOHYUN);
        });
        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).roleOf(DOHYUN)).isEmpty();
        assertThat(scene.recordRepository.records()).hasSize(1);
    }

    @Test
    @DisplayName("거절한 뒤에는 소유자가 같은 이메일로 다시 초대할 수 있다")
    void ownerCanInviteAgainAfterDecline() {
        service.decline(answer(DOHYUN, invitationId));

        long newInvitationId = invite();

        assertThat(newInvitationId).isNotEqualTo(invitationId);
        assertThat(scene.invitationRepository.savedInvitations()).hasSize(2);
    }

    @Test
    @DisplayName("다른 이메일의 운영진 로그인은 거절하지 못한다 (404 STAFF_INVITATION_NOT_FOUND)")
    void anotherEmailCannotDecline() {
        assertRejectedWith(NotFoundException.class, OrganizerErrorCode.STAFF_INVITATION_NOT_FOUND,
                () -> service.decline(answer(YEOREUM, invitationId)));

        assertThat(scene.invitationRepository.findById(invitationId).orElseThrow().status())
                .isEqualTo(StaffInvitationStatus.PENDING);
    }

    @Test
    @DisplayName("유효 기간이 지났거나 이미 답한 초대는 거절하지 못한다")
    void rejectsExpiredOrAnswered() {
        service.decline(answer(DOHYUN, invitationId));
        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.STAFF_INVITATION_ALREADY_ANSWERED,
                () -> service.decline(answer(DOHYUN, invitationId)));

        long secondInvitationId = invite();
        scene.clock.advance(Duration.ofDays(7));
        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.STAFF_INVITATION_EXPIRED,
                () -> service.decline(answer(DOHYUN, secondInvitationId)));
    }
}
