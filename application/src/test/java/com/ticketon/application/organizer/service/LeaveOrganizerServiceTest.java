package com.ticketon.application.organizer.service;

import static com.ticketon.application.organizer.service.OrganizerStaffScene.APPROVED_AT;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.JUNGHOON;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.NOW;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.PLANNER;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.acting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.organizer.port.in.LeaveOrganizerCommand;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 극단 열두달의 기획 단원(운영자)이 스스로 나간다({@link OrganizerStaffScene}). */
@DisplayName("주최자에서 나가기 유스케이스")
class LeaveOrganizerServiceTest {

    private final OrganizerStaffScene scene = new OrganizerStaffScene();
    private final LeaveOrganizerService service = scene.leaveOrganizerService();

    private void assertRejectedWith(Class<? extends DomainException> type, OrganizerErrorCode expectedCode,
            ThrowingCallable action) {
        assertThatExceptionOfType(type)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    @Test
    @DisplayName("운영자가 나가면 소속이 끝나고 이력은 남으며, 운영자로서 조작 기록이 남는다")
    void operatorLeaves() {
        service.leave(new LeaveOrganizerCommand(acting(PLANNER, scene.twelveMonths)));

        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).memberships()).contains(
                new StaffMembership(scene.twelveMonths, PLANNER, StaffRole.OPERATOR, APPROVED_AT.plusSeconds(86_400), NOW));
        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.staffAccountId()).isEqualTo(PLANNER);
            assertThat(record.staffRole()).isEqualTo(StaffRole.OPERATOR);
            assertThat(record.action()).isEqualTo(StaffAction.LEAVE_ORGANIZER);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.STAFF_MEMBER);
            assertThat(record.targetId()).isEqualTo(PLANNER);
            assertThat(record.summary()).isEqualTo("주최자에서 나감");
        });
    }

    @Test
    @DisplayName("나가는 사람의 계정 줄을 먼저 잠근다 — 같은 사람의 운영진 로그인 탈퇴와 같은 잠금 순서(계정 줄 → 소속 줄)")
    void locksOwnAccountFirst() {
        service.leave(new LeaveOrganizerCommand(acting(PLANNER, scene.twelveMonths)));

        assertThat(scene.staffAccountRepository.lockedAccountIds()).containsExactly(PLANNER);
    }

    @Test
    @DisplayName("한 주최자에서 나가도 다른 주최자의 소속은 그대로다")
    void leavingOneOrganizerKeepsOthers() {
        service.leave(new LeaveOrganizerCommand(acting(DOHYUN, scene.lateSummer)));

        assertThat(scene.staffRepository.findByOrganizerId(scene.lateSummer).roleOf(DOHYUN)).isEmpty();
        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).roleOf(PLANNER)).contains(StaffRole.OPERATOR);
    }

    @Test
    @DisplayName("소유자는 나가지 못한다 (409 OWNER_MUST_HAND_OVER_FIRST) — 소속도 기록도 그대로다")
    void ownerCannotLeave() {
        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.OWNER_MUST_HAND_OVER_FIRST,
                () -> service.leave(new LeaveOrganizerCommand(acting(JUNGHOON, scene.twelveMonths))));

        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).currentOwner()).hasValue(JUNGHOON);
        assertThat(scene.recordRepository.records()).isEmpty();
    }

    @Test
    @DisplayName("그 주최자의 운영진이 아니면 나갈 것이 없다 (403 NOT_ORGANIZER_STAFF)")
    void outsiderCannotLeave() {
        assertRejectedWith(PermissionDeniedException.class, OrganizerErrorCode.NOT_ORGANIZER_STAFF,
                () -> service.leave(new LeaveOrganizerCommand(acting(DOHYUN, scene.twelveMonths))));
    }
}
