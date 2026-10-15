package com.ticketon.application.organizer.service;

import static com.ticketon.application.organizer.service.OrganizerStaffScene.APPROVED_AT;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.DOHYUN;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.JUNGHOON;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.NOW;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.PLANNER;
import static com.ticketon.application.organizer.service.OrganizerStaffScene.acting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimCommand;
import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimResult;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.DomainException;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OwnershipClaimReason;
import com.ticketon.domain.organizer.OwnershipClaimStatus;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달의 전 대표 박정훈이 졸업하고 연락이 끊겨, 운영자인 기획 단원이 관리자에게 소유자 이전을 신청한다(docs/01 "소유자와 연락이 끊긴 경우",
 * {@link OrganizerStaffScene}).
 */
@DisplayName("소유자 이전 신청 유스케이스")
class SubmitOwnershipClaimServiceTest {

    private static final OwnershipClaimReason REASON =
            OwnershipClaimReason.of("전 대표가 졸업한 뒤 전화·메일 모두 답이 없습니다. 올해 정기공연을 준비해야 합니다.");

    private final OrganizerStaffScene scene = new OrganizerStaffScene();
    private final SubmitOwnershipClaimService service = scene.submitOwnershipClaimService();

    private SubmitOwnershipClaimResult submit(long claimant, long organizerId) {
        return service.submit(new SubmitOwnershipClaimCommand(acting(claimant, organizerId), REASON));
    }

    private void assertRejectedWith(Class<? extends DomainException> type, OrganizerErrorCode expectedCode,
            ThrowingCallable action) {
        assertThatExceptionOfType(type)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    private void assertNothingSaved() {
        assertThat(scene.ownershipClaimRepository.savedClaims()).isEmpty();
        assertThat(scene.recordRepository.records()).isEmpty();
    }

    @Test
    @DisplayName("운영자가 신청하면 심사 중인 신청이 신청 때의 소유자를 가리켜 저장되고, 소유자는 아직 그대로다")
    void operatorSubmits() {
        SubmitOwnershipClaimResult result = submit(PLANNER, scene.twelveMonths);

        assertThat(result.submittedAt()).isEqualTo(NOW);
        assertThat(scene.ownershipClaimRepository.savedClaims()).singleElement().satisfies(claim -> {
            assertThat(claim.id()).hasValue(result.ownershipClaimId());
            assertThat(claim.organizerId()).isEqualTo(scene.twelveMonths);
            assertThat(claim.claimantStaffAccountId()).isEqualTo(PLANNER);
            assertThat(claim.ownerStaffAccountId()).isEqualTo(JUNGHOON);
            assertThat(claim.reason()).isEqualTo(REASON);
            assertThat(claim.status()).isEqualTo(OwnershipClaimStatus.UNDER_REVIEW);
        });
        OrganizerStaff staff = scene.staffRepository.findByOrganizerId(scene.twelveMonths);
        assertThat(staff.currentOwner()).hasValue(JUNGHOON);
        assertThat(staff.roleOf(PLANNER)).contains(StaffRole.OPERATOR);
    }

    @Test
    @DisplayName("신청한 운영자의 조작 기록이 남고, 메일은 보내지 않는다 — 관리자는 처리 목록으로 받는다")
    void recordsOperationWithoutMail() {
        SubmitOwnershipClaimResult result = submit(PLANNER, scene.twelveMonths);

        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.organizerId()).isEqualTo(scene.twelveMonths);
            assertThat(record.staffAccountId()).isEqualTo(PLANNER);
            assertThat(record.staffRole()).isEqualTo(StaffRole.OPERATOR);
            assertThat(record.action()).isEqualTo(StaffAction.CLAIM_OWNERSHIP);
            assertThat(record.targetKind()).isEqualTo(StaffOperationTargetKind.OWNERSHIP_CLAIM);
            assertThat(record.targetId()).isEqualTo(result.ownershipClaimId());
            assertThat(record.summary()).isEqualTo("소유자 이전 신청");
            assertThat(record.operatedAt()).isEqualTo(NOW);
        });
        assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @Test
    @DisplayName("소유자 본인은 신청하지 못한다 (409 OWNER_CANNOT_CLAIM_OWNERSHIP) — 운영자에게 직접 넘긴다")
    void ownerCannotClaim() {
        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.OWNER_CANNOT_CLAIM_OWNERSHIP,
                () -> submit(JUNGHOON, scene.twelveMonths));

        assertNothingSaved();
    }

    @Test
    @DisplayName("그 주최자의 운영진이 아니면 신청하지 못하고(403 NOT_ORGANIZER_STAFF), 닫힌 주최자에도 못 한다")
    void outsiderCannotClaim() {
        assertRejectedWith(PermissionDeniedException.class, OrganizerErrorCode.NOT_ORGANIZER_STAFF,
                () -> submit(DOHYUN, scene.twelveMonths));
        assertThatExceptionOfType(PermissionDeniedException.class).isThrownBy(() -> submit(PLANNER, scene.closedSpring));

        assertNothingSaved();
    }

    @Test
    @DisplayName("이 주최자에 심사 중인 신청을 이미 냈으면 결과를 기다린다 (409 OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW)")
    void waitsForPendingClaim() {
        submit(PLANNER, scene.twelveMonths);

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW,
                () -> submit(PLANNER, scene.twelveMonths));
        assertThat(scene.ownershipClaimRepository.savedClaims()).hasSize(1);
        assertThat(scene.recordRepository.records()).hasSize(1);
    }

    @Test
    @DisplayName("확인한 뒤 같은 사람의 다른 신청이 먼저 저장됐으면 409이고 조작 기록도 남지 않는다")
    void concurrentSubmissionLosesWithConflict() {
        scene.ownershipClaimRepository.letAnotherSubmissionWinNextAdd();

        assertRejectedWith(ConflictException.class, OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW,
                () -> submit(PLANNER, scene.twelveMonths));
        assertThat(scene.recordRepository.records()).isEmpty();
    }

    @Test
    @DisplayName("신청하는 사람과 지금 소유자의 계정 줄을 id 순서로 먼저 잠근다 — 신청 줄이 두 사람을 가리키므로 소유자 넘기기 · 내보내기와 같은 순서")
    void locksClaimantAndOwnerAccountsInIdOrder() {
        submit(PLANNER, scene.twelveMonths);

        assertThat(scene.staffAccountRepository.lockedAccountIds()).containsExactly(JUNGHOON, PLANNER);
    }

    @Test
    @DisplayName("잠금을 기다리는 사이 그 사람이 나갔으면 잠근 뒤의 명단에서 막힌다 (403 NOT_ORGANIZER_STAFF)")
    void claimantLeftWhileWaitingForLock() {
        scene.staffAccountRepository.runOnNextLock(() -> {
            OrganizerStaff staff = scene.staffRepository.findByOrganizerId(scene.twelveMonths);
            scene.staffRepository.saveEnded(staff.leave(PLANNER, NOW));
        });

        assertRejectedWith(PermissionDeniedException.class, OrganizerErrorCode.NOT_ORGANIZER_STAFF,
                () -> submit(PLANNER, scene.twelveMonths));
        assertThat(scene.staffAccountRepository.lockedAccountIds()).containsExactly(JUNGHOON, PLANNER);
        assertNothingSaved();
    }

    @Test
    @DisplayName("잠금을 기다리는 사이 소유자가 다른 운영자에게 넘어갔으면 409 OWNERSHIP_ALREADY_TRANSFERRED다 — 잠그지 않은 사람을 가리키는 신청을 넣지 않는다")
    void ownerChangedToAnotherWhileWaitingForLock() {
        scene.staffAccountRepository.storeActive(5, "정홍보", "구름", "promoter@example.com");
        OrganizerStaff staff = scene.staffRepository.findByOrganizerId(scene.twelveMonths);
        staff.addOperator(5, APPROVED_AT.plusSeconds(172_800));
        scene.staffRepository.store(staff);
        scene.staffAccountRepository.runOnNextLock(() -> scene.staffRepository.saveOwnershipTransfer(
                scene.staffRepository.findByOrganizerId(scene.twelveMonths).transferOwnership(JUNGHOON, 5, NOW)));

        assertRejectedWith(ConflictException.class, OrganizerErrorCode.OWNERSHIP_ALREADY_TRANSFERRED,
                () -> submit(PLANNER, scene.twelveMonths));
        assertNothingSaved();
    }

    @Test
    @DisplayName("잠금을 기다리는 사이 신청하는 사람이 소유자를 넘겨받았으면 소유자라 신청하지 못한다 (409 OWNER_CANNOT_CLAIM_OWNERSHIP)")
    void claimantBecameOwnerWhileWaitingForLock() {
        scene.staffAccountRepository.runOnNextLock(() -> scene.staffRepository.saveOwnershipTransfer(
                scene.staffRepository.findByOrganizerId(scene.twelveMonths).transferOwnership(JUNGHOON, PLANNER, NOW)));

        assertRejectedWith(RuleViolationException.class, OrganizerErrorCode.OWNER_CANNOT_CLAIM_OWNERSHIP,
                () -> submit(PLANNER, scene.twelveMonths));
        assertNothingSaved();
    }
}
