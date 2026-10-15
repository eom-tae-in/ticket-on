package com.ticketon.application.organizer.service;

import static com.ticketon.application.organizer.service.OrganizerStaffScene.APPROVED_AT;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.JUNGHOON;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.NOW;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.PLANNER;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.PLANNER_EMAIL;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.acting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.RemoveOperatorCommand;
import com.ticketon.domain.account.EmailAddress;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.ContactPoint;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 극단 열두달의 대표 박정훈이 기획 단원(운영자)을 내보낸다({@link OrganizerStaffScene}). */
@DisplayName("운영자 내보내기 유스케이스")
class RemoveOperatorServiceTest {

    private final OrganizerStaffScene scene = new OrganizerStaffScene();
    private final RemoveOperatorService service = scene.removeOperatorService();

    private void remove(long requestedBy, long operator) {
        service.remove(new RemoveOperatorCommand(acting(requestedBy, scene.twelveMonths), operator));
    }

    private void assertRejectedWith(Class<? extends DomainException> type, OrganizerErrorCode expectedCode,
            ThrowingCallable action) {
        assertThatExceptionOfType(type)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    @Test
    @DisplayName("소유자가 내보내면 소속이 끝나고 이력은 남으며, 조작 기록이 남고, 내보내진 사람에게 메일이 간다")
    void ownerRemovesOperator() {
        remove(JUNGHOON, PLANNER);

        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).memberships()).contains(
                new StaffMembership(scene.twelveMonths, PLANNER, StaffRole.OPERATOR, APPROVED_AT.plusSeconds(86_400), NOW));
        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.staffAccountId()).isEqualTo(JUNGHOON);
            assertThat(record.staffRole()).isEqualTo(StaffRole.OWNER);
            assertThat(record.action()).isEqualTo(StaffAction.REMOVE_STAFF);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.STAFF_MEMBER);
            assertThat(record.targetId()).isEqualTo(PLANNER);
            assertThat(record.summary()).isEqualTo("운영자 내보내기 · 푸른바람");
        });
        SendNotificationCommand sent = scene.sendNotificationUseCase.sentCommands().getFirst();
        assertThat(scene.sendNotificationUseCase.sentCommands()).hasSize(1);
        assertThat(sent.kind()).isEqualTo(NotificationKind.REMOVED_FROM_STAFF);
        assertThat(sent.recipient()).isEqualTo(NotificationRecipient.contact(
                ContactPoint.emailOnly(EmailAddress.of(PLANNER_EMAIL))));
        assertThat(sent.content().title()).isEqualTo("'극단 열두달' 운영진에서 내보내졌습니다");
        assertThat(sent.content().body()).contains("이제 이 주최자의 주최 관리에 들어갈 수 없습니다.");
    }

    @Test
    @DisplayName("내보내진 사람은 그 주최자의 주최 관리에서 403 NOT_ORGANIZER_STAFF다")
    void removedOperatorLosesAccess() {
        remove(JUNGHOON, PLANNER);

        assertRejectedWith(PermissionDeniedException.class, OrganizerErrorCode.NOT_ORGANIZER_STAFF,
                () -> scene.checkStaffPermissionService.check(
                        new CheckStaffPermissionQuery(acting(PLANNER, scene.twelveMonths), StaffAction.VIEW_STATUS)));
    }

    @Test
    @DisplayName("운영자는 내보내지 못한다 (403 OWNER_ONLY_ACTION) — 소속 · 기록 · 메일이 그대로다")
    void operatorCannotRemove() {
        assertRejectedWith(PermissionDeniedException.class, OrganizerErrorCode.OWNER_ONLY_ACTION,
                () -> remove(PLANNER, JUNGHOON));

        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).roleOf(JUNGHOON)).contains(StaffRole.OWNER);
        assertThat(scene.recordRepository.records()).isEmpty();
        assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @Test
    @DisplayName("소유자 자신이나 그 주최자의 운영자가 아닌 사람은 내보내지 못한다 (409 NOT_AN_OPERATOR)")
    void cannotRemoveOwnerOrOutsider() {
        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.NOT_AN_OPERATOR, () -> remove(JUNGHOON, JUNGHOON));
        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.NOT_AN_OPERATOR, () -> remove(JUNGHOON, DOHYUN));

        assertThat(scene.recordRepository.records()).isEmpty();
        assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
    }
}
