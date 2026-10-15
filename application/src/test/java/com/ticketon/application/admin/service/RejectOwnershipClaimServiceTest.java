package com.ticketon.application.admin.service;

import static com.ticketon.application.admin.service.OwnershipClaimScene.JUNGHOON;
import static com.ticketon.application.admin.service.OwnershipClaimScene.NOW;
import static com.ticketon.application.admin.service.OwnershipClaimScene.PLANNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.admin.port.in.ApproveOwnershipClaimCommand;
import com.ticketon.application.admin.port.in.RejectOwnershipClaimCommand;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.admin.AdminOperationKind;
import com.ticketon.domain.admin.AdminOperationTargetKind;
import com.ticketon.domain.error.ConflictException;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import com.ticketon.domain.notification.NotificationKind;
import com.ticketon.domain.notification.NotificationRecipient;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OwnershipClaimStatus;
import com.ticketon.domain.organizer.RejectionReason;
import com.ticketon.domain.organizer.StaffRole;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 관리자가 소유자와 통화해 보니 직접 넘기기로 해, 기획 단원의 소유자 이전 신청을 반려한다({@link OwnershipClaimScene}). */
@DisplayName("소유자 이전 신청 반려 유스케이스")
class RejectOwnershipClaimServiceTest {

    private static final RejectionReason REASON = RejectionReason.of("소유자와 통화해 직접 넘기기로 함");

    private static final String JUNGHOON_EMAIL = "junghoon@example.com";
    private static final String PLANNER_EMAIL = "planner@example.com";
    private static final String PROMOTER_EMAIL = "promoter@example.com";

    private final OwnershipClaimScene scene = new OwnershipClaimScene();
    private final RejectOwnershipClaimService service = scene.rejectService();
    private final AccountRef admin = scene.storeAdmin(AdminGrade.ADMIN);

    private void reject(long claimId) {
        service.reject(new RejectOwnershipClaimCommand(admin, claimId, REASON));
    }

    @Test
    @DisplayName("반려하면 사유와 처리 일시가 남고 소유자 · 소속은 그대로다 — 소유자 변경 이력도 없다")
    void rejectionKeepsOwner() {
        reject(scene.plannerClaim);

        assertThat(scene.claimRepository.findById(scene.plannerClaim).orElseThrow()).satisfies(claim -> {
            assertThat(claim.status()).isEqualTo(OwnershipClaimStatus.REJECTED);
            assertThat(claim.reviewedAt()).contains(NOW);
            assertThat(claim.rejectionReason()).contains(REASON);
        });
        OrganizerStaff staff = scene.staffRepository.findByOrganizerId(scene.twelveMonths);
        assertThat(staff.currentOwner()).hasValue(JUNGHOON);
        assertThat(staff.roleOf(PLANNER)).contains(StaffRole.OPERATOR);
        assertThat(scene.ownershipChangeRepository.savedChanges()).isEmpty();
    }

    @Test
    @DisplayName("사유와 함께 관리자 조작 기록이 남는다")
    void recordsAdminOperationWithReason() {
        reject(scene.plannerClaim);

        assertThat(scene.recordRepository.records()).singleElement().satisfies(record -> {
            assertThat(record.operator()).isEqualTo(admin);
            assertThat(record.kind()).isEqualTo(AdminOperationKind.REJECT_OWNERSHIP_CLAIM);
            assertThat(record.targetKind()).isEqualTo(AdminOperationTargetKind.OWNERSHIP_CLAIM);
            assertThat(record.targetId()).isEqualTo(scene.plannerClaim);
            assertThat(record.summary()).isEqualTo("소유자 이전 신청 반려 — 극단 열두달");
            assertThat(record.reason()).hasValue("소유자와 통화해 직접 넘기기로 함");
        });
    }

    @Test
    @DisplayName("사유를 담은 반려 메일이 신청한 운영자와 운영진 전원에게 한 사람에 한 통씩 간다 — 신청자는 운영진과 겹쳐도 한 번만")
    void notifiesClaimantAndAllStaffOnce() {
        reject(scene.plannerClaim);

        assertThat(recipientsOfSentMails()).containsExactly(PLANNER_EMAIL, JUNGHOON_EMAIL, PROMOTER_EMAIL);
        assertThat(scene.sendNotificationUseCase.sentCommands()).allSatisfy(sent -> {
            assertThat(sent.kind()).isEqualTo(NotificationKind.OWNERSHIP_CLAIM_REJECTED);
            assertThat(sent.content().title()).isEqualTo("'극단 열두달' 소유자 이전 신청이 반려되었습니다");
            assertThat(sent.content().body())
                    .contains("소유자는 바뀌지 않았습니다.")
                    .contains("신청한 분: 푸른바람 님")
                    .contains("반려 사유: 소유자와 통화해 직접 넘기기로 함");
        });
    }

    @Test
    @DisplayName("결과 전에 주최자에서 나간 신청자도 반려 메일을 한 번 받는다 — 운영진 전원은 그대로 받는다")
    void claimantWhoLeftStillGetsResult() {
        OrganizerStaff staff = scene.staffRepository.findByOrganizerId(scene.twelveMonths);
        staff.leave(PLANNER, NOW.minusSeconds(3_600));
        scene.staffRepository.store(staff);

        reject(scene.plannerClaim);

        assertThat(recipientsOfSentMails()).containsExactly(PLANNER_EMAIL, JUNGHOON_EMAIL, PROMOTER_EMAIL);
    }

    @Test
    @DisplayName("운영진 로그인을 탈퇴한 신청자에게는 보내지 않는다 — 이메일을 파기했다. 운영진 전원은 받는다")
    void withdrawnClaimantGetsNoMail() {
        OrganizerStaff staff = scene.staffRepository.findByOrganizerId(scene.twelveMonths);
        staff.leave(PLANNER, NOW.minusSeconds(3_600));
        scene.staffRepository.store(staff);
        scene.staffAccountRepository.markWithdrawn(PLANNER);

        reject(scene.plannerClaim);

        assertThat(recipientsOfSentMails()).containsExactly(JUNGHOON_EMAIL, PROMOTER_EMAIL);
        assertThat(scene.sendNotificationUseCase.sentCommands()).allSatisfy(sent ->
                assertThat(sent.content().body()).contains("신청한 분: 푸른바람 님"));
    }

    /** 보낸 메일의 받는 이메일 — 보낸 차례대로. */
    private List<String> recipientsOfSentMails() {
        return scene.sendNotificationUseCase.sentCommands().stream()
                .map(sent -> ((NotificationRecipient.DirectContact) sent.recipient()).contactPoint().email()
                        .orElseThrow().value())
                .toList();
    }

    @Test
    @DisplayName("승인한 신청은 반려하지 못한다 (409 OWNERSHIP_CLAIM_ALREADY_REVIEWED) — 기록 · 메일이 더 남지 않는다")
    void cannotRejectApproved() {
        scene.approveService().approve(new ApproveOwnershipClaimCommand(admin, scene.plannerClaim));

        assertThatExceptionOfType(RuleViolationException.class)
                .isThrownBy(() -> reject(scene.plannerClaim))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_REVIEWED));
        assertThat(scene.recordRepository.records()).hasSize(1);
        assertThat(scene.sendNotificationUseCase.sentCommands()).singleElement()
                .satisfies(sent -> assertThat(sent.kind()).isEqualTo(NotificationKind.OWNER_CHANGED));
    }

    @Test
    @DisplayName("다른 관리자가 거의 동시에 먼저 처리했으면 신청 저장에서 409이고 기록 · 메일이 남지 않는다")
    void concurrentReviewLosesAtSave() {
        scene.claimRepository.letAnotherReviewWinNextSave();

        assertThatExceptionOfType(ConflictException.class)
                .isThrownBy(() -> reject(scene.plannerClaim))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_REVIEWED));
        assertThat(scene.recordRepository.records()).isEmpty();
        assertThat(scene.sendNotificationUseCase.sentCommands()).isEmpty();
    }

    @Test
    @DisplayName("없는 신청이면 404 OWNERSHIP_CLAIM_NOT_FOUND다")
    void unknownClaim() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> reject(scene.plannerClaim + 1))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.OWNERSHIP_CLAIM_NOT_FOUND));
        assertThat(scene.recordRepository.records()).isEmpty();
    }
}
