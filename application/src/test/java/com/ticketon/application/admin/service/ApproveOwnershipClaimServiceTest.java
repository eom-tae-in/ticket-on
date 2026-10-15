package com.ticketon.application.admin.service;

import static com.ticketon.application.admin.service.OwnershipClaimScene.APPROVED_AT;
import static com.ticketon.application.admin.service.OwnershipClaimScene.JUNGHOON;
import static com.ticketon.application.admin.service.OwnershipClaimScene.NOW;
import static com.ticketon.application.admin.service.OwnershipClaimScene.PLANNER;
import static com.ticketon.application.admin.service.OwnershipClaimScene.PROMOTER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.admin.port.in.ApproveOwnershipClaimCommand;
import com.ticketon.application.notification.port.in.SendNotificationCommand;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.admin.AdminOperationKind;
import com.ticketon.domain.admin.AdminOperationTargetKind;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OwnershipChangeMethod;
import com.ticketon.domain.organizer.OwnershipClaimStatus;
import com.ticketon.domain.organizer.OwnershipTransfer;
import com.ticketon.domain.organizer.StaffMembership;
import com.ticketon.domain.organizer.StaffRole;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** 관리자가 기획 단원의 소유자 이전 신청을 확인한 뒤 승인한다({@link OwnershipClaimScene}). */
@DisplayName("소유자 이전 신청 승인 유스케이스 (docs/01 \"소유자와 연락이 끊긴 경우\", docs/11 \"대표가 바뀔 때\")")
class ApproveOwnershipClaimServiceTest {

    private final OwnershipClaimScene scene = new OwnershipClaimScene();
    private final ApproveOwnershipClaimService service = scene.approveService();
    private final AccountRef admin = scene.storeAdmin(AdminGrade.ADMIN);

    private void approve(long claimId) {
        service.approve(new ApproveOwnershipClaimCommand(admin, claimId));
    }

    private void assertRejectedWith(Class<? extends DomainException> type, OrganizerErrorCode expectedCode,
            ThrowingCallable action) {
        assertThatExceptionOfType(type)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    /** 승인이 막혔으면 소유자 · 이력 · 기록 · 메일 어느 것도 바뀌거나 남지 않는다. */
    private void assertNothingApproved(long expectedOwner) {
        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).currentOwner()).hasValue(expectedOwner);
        assertThat(scene.ownershipChangeRepository.savedChanges()).isEmpty();
        assertThat(scene.recordRepository.records()).isEmpty();
        assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(AdminGrade.class)
    @DisplayName("승인하면 신청한 운영자가 소유자가 되고 옛 소유자는 운영자로 남는다 — 관리자 · 최고 관리자 모두 한다")
    void approvalSwapsRoles(AdminGrade grade) {
        service.approve(new ApproveOwnershipClaimCommand(scene.storeAdmin(grade), scene.plannerClaim));

        OrganizerStaff staff = scene.staffRepository.findByOrganizerId(scene.twelveMonths);
        assertThat(staff.currentOwner()).hasValue(PLANNER);
        assertThat(staff.roleOf(JUNGHOON)).contains(StaffRole.OPERATOR);
        assertThat(staff.roleOf(PROMOTER)).contains(StaffRole.OPERATOR);
        assertThat(staff.memberships()).contains(
                new StaffMembership(scene.twelveMonths, JUNGHOON, StaffRole.OWNER, APPROVED_AT, NOW),
                new StaffMembership(scene.twelveMonths, PLANNER, StaffRole.OPERATOR, APPROVED_AT, NOW),
                new StaffMembership(scene.twelveMonths, JUNGHOON, StaffRole.OPERATOR, NOW, null),
                new StaffMembership(scene.twelveMonths, PLANNER, StaffRole.OWNER, NOW, null));
        assertThat(scene.claimRepository.findById(scene.plannerClaim).orElseThrow()).satisfies(claim -> {
            assertThat(claim.status()).isEqualTo(OwnershipClaimStatus.APPROVED);
            assertThat(claim.reviewedAt()).contains(NOW);
        });
    }

    @Test
    @DisplayName("소유자 변경 이력은 '신청 승인'으로 그 신청을 가리켜 남는다 — 사후 확인 대상이 아니라 처리 목록 '소유자 변경'에 쌓이지 않는다")
    void recordsChangeAsClaimApproval() {
        approve(scene.plannerClaim);

        assertThat(scene.ownershipChangeRepository.savedChanges()).singleElement().satisfies(change -> {
            assertThat(change.transfer()).isEqualTo(new OwnershipTransfer(scene.twelveMonths, JUNGHOON, PLANNER, NOW));
            assertThat(change.method()).isEqualTo(OwnershipChangeMethod.CLAIM_APPROVAL);
            assertThat(change.method().needsAdminConfirmation()).isFalse();
            assertThat(change.ownershipClaimId()).hasValue(scene.plannerClaim);
        });
    }

    @Test
    @DisplayName("관리자 조작 기록이 남는다 — 누가 · 언제 · 소유자 이전 신청 승인 · 신청 id · 주최자명")
    void recordsAdminOperation() {
        approve(scene.plannerClaim);

        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.operator()).isEqualTo(admin);
            assertThat(record.operatedAt()).isEqualTo(NOW);
            assertThat(record.kind()).isEqualTo(AdminOperationKind.APPROVE_OWNERSHIP_CLAIM);
            assertThat(record.targetKind()).isEqualTo(AdminOperationTargetKind.OWNERSHIP_CLAIM);
            assertThat(record.targetId()).isEqualTo(scene.plannerClaim);
            assertThat(record.summary()).isEqualTo("소유자 이전 신청 승인 — 극단 열두달");
            assertThat(record.reason()).isEmpty();
        });
    }

    @Test
    @DisplayName("바뀐 뒤의 운영진 전원에게 '소유자 변경' 메일이 한 번 간다 — 신청한 사람에게 가는 결과 알림이기도 하다")
    void notifiesAllStaff() {
        approve(scene.plannerClaim);

        assertThat(scene.sendNotificationUseCase.sentCommands()).hasSize(1);
        SendNotificationCommand sent = scene.sendNotificationUseCase.sentCommands().getFirst();
        assertThat(sent.kind()).isEqualTo(NotificationKind.OWNER_CHANGED);
        assertThat(sent.recipient()).isEqualTo(NotificationRecipient.organizer(scene.twelveMonths));
        assertThat(sent.content().title()).isEqualTo("'극단 열두달' 소유자가 바뀌었습니다");
        assertThat(sent.content().body())
                .contains("'극단 열두달'의 소유자 이전 신청이 승인되어 소유자가 바뀌었습니다.")
                .contains("푸른바람 님이 새 소유자가 되었고, 조용한고래 님은 운영자로 남습니다.");
    }

    @Test
    @DisplayName("신청한 사람과 신청 때의 소유자의 계정 줄을 id 순서로 먼저 잠근다 — 잠금 순서 계정 줄 → 소속 줄")
    void locksAccountsInIdOrder() {
        approve(scene.plannerClaim);

        assertThat(scene.staffAccountRepository.lockedAccountIds()).containsExactly(JUNGHOON, PLANNER);
    }

    @Test
    @DisplayName("이미 처리한 신청은 다시 승인하지 못한다 (409 OWNERSHIP_CLAIM_ALREADY_REVIEWED)")
    void approvesOnlyOnce() {
        scene.claimRepository.rejectInTable(scene.plannerClaim, "정보가 부족함", NOW);

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_REVIEWED,
                () -> approve(scene.plannerClaim));
        assertNothingApproved(JUNGHOON);
    }

    @Test
    @DisplayName("계정 잠금을 기다리는 사이 다른 관리자가 먼저 승인했으면 잠근 뒤 다시 읽은 신청을 보고 409 OWNERSHIP_CLAIM_ALREADY_REVIEWED다")
    void concurrentApprovalLosesAfterLock() {
        AccountRef otherAdmin = scene.storeAdmin(AdminGrade.SUPER_ADMIN);
        scene.staffAccountRepository.runOnNextLock(() -> scene.approveService()
                .approve(new ApproveOwnershipClaimCommand(otherAdmin, scene.plannerClaim)));

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_REVIEWED,
                () -> approve(scene.plannerClaim));
        assertThat(scene.staffRepository.findByOrganizerId(scene.twelveMonths).currentOwner()).hasValue(PLANNER);
        assertThat(scene.ownershipChangeRepository.savedChanges()).hasSize(1);
        assertThat(scene.recordRepository.records()).singleElement()
                .satisfies(record -> assertThat(record.operator()).isEqualTo(otherAdmin));
        assertThat(scene.sendNotificationUseCase.sentCommands()).hasSize(1);
    }

    @Test
    @DisplayName("읽고 규칙은 통과했는데 다른 관리자가 먼저 반려했으면 신청 저장에서 409이고 아무것도 남지 않는다")
    void concurrentRejectionWinsAtSave() {
        scene.claimRepository.letAnotherReviewWinNextSave();

        assertRejectedWith(ConflictException.class, OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_REVIEWED,
                () -> approve(scene.plannerClaim));
        assertNothingApproved(JUNGHOON);
    }

    @Test
    @DisplayName("신청 뒤 소유자가 돌아와 직접 넘겼으면 이 신청으로는 바꾸지 않는다 (409 OWNERSHIP_ALREADY_TRANSFERRED) — 반려한다")
    void rejectsWhenOwnerAlreadyChanged() {
        OrganizerStaff staff = scene.staffRepository.findByOrganizerId(scene.twelveMonths);
        scene.staffRepository.saveOwnershipTransfer(staff.transferOwnership(JUNGHOON, PROMOTER, NOW.minusSeconds(60)));

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.OWNERSHIP_ALREADY_TRANSFERRED,
                () -> approve(scene.plannerClaim));
        assertNothingApproved(PROMOTER);
    }

    @Test
    @DisplayName("신청한 사람이 운영진 로그인을 탈퇴했으면 승인하지 못한다 (409 CLAIMANT_NOT_OPERATOR) — 탈퇴한 계정에 소유자를 넘기지 않는다")
    void rejectsWithdrawnClaimant() {
        scene.staffAccountRepository.markWithdrawn(PLANNER);

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.CLAIMANT_NOT_OPERATOR,
                () -> approve(scene.plannerClaim));
        assertNothingApproved(JUNGHOON);
    }

    @Test
    @DisplayName("없는 신청이면 404 OWNERSHIP_CLAIM_NOT_FOUND다")
    void unknownClaim() {
        assertRejectedWith(NotFoundException.class, OrganizerErrorCode.OWNERSHIP_CLAIM_NOT_FOUND,
                () -> approve(scene.plannerClaim + 1));
        assertNothingApproved(JUNGHOON);
    }
}
