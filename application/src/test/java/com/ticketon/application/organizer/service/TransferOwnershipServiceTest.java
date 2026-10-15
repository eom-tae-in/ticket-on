package com.ticketon.application.organizer.service;

import static com.ticketon.application.organizer.service.OrganizerStaffScene.APPROVED_AT;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.JUNGHOON;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.NOW;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.PLANNER;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.acting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.LeaveOrganizerCommand;
import com.ticketon.application.organizer.port.in.TransferOwnershipCommand;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OwnershipTransfer;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 극단 열두달의 대표 박정훈이 기획 단원(운영자)에게 소유자를 넘긴다 — 동아리처럼 대표가 바뀐다({@link OrganizerStaffScene}). */
@DisplayName("소유자 넘기기 유스케이스")
class TransferOwnershipServiceTest {

    private final OrganizerStaffScene scene = new OrganizerStaffScene();
    private final TransferOwnershipService service = scene.transferOwnershipService();

    private void transfer(long requestedBy, long newOwner) {
        service.transfer(new TransferOwnershipCommand(acting(requestedBy, scene.twelveMonths), newOwner));
    }

    private void assertRejectedWith(Class<? extends DomainException> type, OrganizerErrorCode expectedCode,
            ThrowingCallable action) {
        assertThatExceptionOfType(type)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    private void assertNothingChanged() {
        OrganizerStaff staff = scene.staffRepository.findByOrganizerId(scene.twelveMonths);
        assertThat(staff.currentOwner()).hasValue(JUNGHOON);
        assertThat(staff.roleOf(PLANNER)).contains(StaffRole.OPERATOR);
        assertThat(scene.ownershipChangeRepository.savedChanges()).isEmpty();
        assertThat(scene.recordRepository.records()).isEmpty();
        assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @Test
    @DisplayName("넘기면 받은 사람이 소유자, 넘긴 사람은 운영자로 남고 지난 역할은 이력으로 남는다 — 소유자는 여전히 1명이다")
    void swapsRoles() {
        transfer(JUNGHOON, PLANNER);

        OrganizerStaff staff = scene.staffRepository.findByOrganizerId(scene.twelveMonths);
        assertThat(staff.currentOwner()).hasValue(PLANNER);
        assertThat(staff.roleOf(JUNGHOON)).contains(StaffRole.OPERATOR);
        assertThat(staff.memberships()).contains(
                new StaffMembership(scene.twelveMonths, JUNGHOON, StaffRole.OWNER, APPROVED_AT, NOW),
                new StaffMembership(scene.twelveMonths, PLANNER, StaffRole.OPERATOR, APPROVED_AT.plusSeconds(86_400), NOW),
                new StaffMembership(scene.twelveMonths, JUNGHOON, StaffRole.OPERATOR, NOW, null),
                new StaffMembership(scene.twelveMonths, PLANNER, StaffRole.OWNER, NOW, null));
    }

    @Test
    @DisplayName("넘기면 확인 전인 소유자 변경 이력이 남고(관리자 처리 목록), 넘긴 소유자의 조작 기록이 남는다")
    void recordsChangeAndOperation() {
        transfer(JUNGHOON, PLANNER);

        assertThat(scene.ownershipChangeRepository.savedChanges()).singleElement().satisfies(change -> {
            assertThat(change.transfer()).isEqualTo(new OwnershipTransfer(scene.twelveMonths, JUNGHOON, PLANNER, NOW));
            assertThat(change.isConfirmed()).isFalse();
        });
        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.staffAccountId()).isEqualTo(JUNGHOON);
            assertThat(record.staffRole()).isEqualTo(StaffRole.OWNER);
            assertThat(record.action()).isEqualTo(StaffAction.TRANSFER_OWNERSHIP);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.STAFF_MEMBER);
            assertThat(record.targetId()).isEqualTo(PLANNER);
            assertThat(record.summary()).isEqualTo("소유자 넘기기 · 푸른바람");
        });
    }

    @Test
    @DisplayName("넘기면 그 주최자의 운영진 전원에게 '소유자 변경' 메일이 한 번 간다 — 누가 누구에게 넘겼는지 닉네임으로 적는다")
    void notifiesAllStaff() {
        transfer(JUNGHOON, PLANNER);

        assertThat(scene.sendNotificationUseCase.sentCommands()).hasSize(1);
        SendNotificationCommand sent = scene.sendNotificationUseCase.sentCommands().getFirst();
        assertThat(sent.kind()).isEqualTo(NotificationKind.OWNER_CHANGED);
        assertThat(sent.recipient()).isEqualTo(NotificationRecipient.organizer(scene.twelveMonths));
        assertThat(sent.content().title()).isEqualTo("'극단 열두달' 소유자가 바뀌었습니다");
        assertThat(sent.content().body()).contains("조용한고래 님이 푸른바람 님에게 소유자를 넘겼고, 조용한고래 님은 운영자로 남습니다.");
    }

    @Test
    @DisplayName("새 소유자가 소유자만 하는 일을 하고, 넘긴 사람은 운영자 권한만 남는다")
    void permissionsFollowNewRoles() {
        transfer(JUNGHOON, PLANNER);

        assertThat(scene.checkStaffPermissionService.check(new CheckStaffPermissionQuery(
                acting(PLANNER, scene.twelveMonths), StaffAction.MANAGE_SETTLEMENT_ACCOUNT)).role()).isEqualTo(StaffRole.OWNER);
        assertRejectedWith(PermissionDeniedException.class, OrganizerErrorCode.OWNER_ONLY_ACTION,
                () -> scene.checkStaffPermissionService.check(new CheckStaffPermissionQuery(
                        acting(JUNGHOON, scene.twelveMonths), StaffAction.MANAGE_SETTLEMENT_ACCOUNT)));
    }

    @Test
    @DisplayName("운영자가 아닌 사람에게는 넘기지 못한다 (409 OWNERSHIP_RECEIVER_NOT_OPERATOR) — 바깥 사람, 소유자 자신, 없는 사람")
    void rejectsReceiverWhoIsNotOperator() {
        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.OWNERSHIP_RECEIVER_NOT_OPERATOR,
                () -> transfer(JUNGHOON, DOHYUN));
        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.OWNERSHIP_RECEIVER_NOT_OPERATOR,
                () -> transfer(JUNGHOON, JUNGHOON));
        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.OWNERSHIP_RECEIVER_NOT_OPERATOR,
                () -> transfer(JUNGHOON, 99));

        assertNothingChanged();
    }

    @Test
    @DisplayName("이미 나간 운영자에게는 넘기지 못한다 (409 OWNERSHIP_RECEIVER_NOT_OPERATOR)")
    void rejectsFormerOperator() {
        scene.leaveOrganizerService().leave(new LeaveOrganizerCommand(acting(PLANNER, scene.twelveMonths)));

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.OWNERSHIP_RECEIVER_NOT_OPERATOR,
                () -> transfer(JUNGHOON, PLANNER));
        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).currentOwner()).hasValue(JUNGHOON);
        assertThat(scene.recordRepository.records()).extracting(record -> record.action())
                .containsExactly(StaffAction.LEAVE_ORGANIZER);
        assertThat(scene.ownershipChangeRepository.savedChanges()).isEmpty();
        assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @Test
    @DisplayName("운영자는 넘기지 못하고(403 OWNER_ONLY_ACTION), 다른 주최자의 운영진도 못 한다(403 NOT_ORGANIZER_STAFF)")
    void onlyOwnerTransfers() {
        assertRejectedWith(PermissionDeniedException.class, OrganizerErrorCode.OWNER_ONLY_ACTION,
                () -> transfer(PLANNER, JUNGHOON));
        assertRejectedWith(PermissionDeniedException.class, OrganizerErrorCode.NOT_ORGANIZER_STAFF,
                () -> transfer(DOHYUN, PLANNER));

        assertNothingChanged();
    }

    @Test
    @DisplayName("넘긴 뒤 옛 소유자가 다시 넘기려 하면 소유자가 아니라 막힌다 (403 OWNER_ONLY_ACTION) — 새 소유자는 다시 넘길 수 있다")
    void formerOwnerCannotTransferAgain() {
        transfer(JUNGHOON, PLANNER);

        assertRejectedWith(PermissionDeniedException.class, OrganizerErrorCode.OWNER_ONLY_ACTION,
                () -> transfer(JUNGHOON, PLANNER));
        transfer(PLANNER, JUNGHOON);

        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).currentOwner()).hasValue(JUNGHOON);
        assertThat(scene.ownershipChangeRepository.savedChanges()).hasSize(2);
    }
}
