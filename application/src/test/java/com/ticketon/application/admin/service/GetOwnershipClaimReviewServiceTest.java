package com.ticketon.application.admin.service;

import static com.ticketon.application.admin.service.OwnershipClaimScene.CLAIM_REASON;
import static com.ticketon.application.admin.service.OwnershipClaimScene.JUNGHOON;
import static com.ticketon.application.admin.service.OwnershipClaimScene.NOW;
import static com.ticketon.application.admin.service.OwnershipClaimScene.PLANNER;
import static com.ticketon.application.admin.service.OwnershipClaimScene.PROMOTER;
import static com.ticketon.application.admin.service.OwnershipClaimScene.SUBMITTED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.admin.port.in.ApproveOwnershipClaimCommand;
import com.ticketon.application.admin.port.in.GetOwnershipClaimReviewQuery;
import com.ticketon.application.admin.port.in.OwnershipClaimReviewResult;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OwnershipClaimStatus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 관리자가 처리 목록에서 기획 단원의 소유자 이전 신청을 눌러 심사 화면을 연다({@link OwnershipClaimScene}). */
@DisplayName("소유자 이전 신청 심사 화면 조회 (docs/11 \"대표가 바뀔 때 — 주최자 신청 때 받은 연락처와 SNS 등으로 확인\")")
class GetOwnershipClaimReviewServiceTest {

    private final OwnershipClaimScene scene = new OwnershipClaimScene();
    private final GetOwnershipClaimReviewService service = scene.reviewService();
    private final AccountRef admin = scene.storeAdmin(AdminGrade.ADMIN);

    private OwnershipClaimReviewResult review(long claimId) {
        return service.getReview(new GetOwnershipClaimReviewQuery(admin, claimId));
    }

    @Test
    @DisplayName("신청 사유 · 신청한 운영자 · 신청 때의 소유자 · 지금 소유자를 닉네임으로 보여 준다")
    void showsClaimAndPeople() {
        OwnershipClaimReviewResult result = review(scene.plannerClaim);

        assertThat(result.ownershipClaimId()).isEqualTo(scene.plannerClaim);
        assertThat(result.organizerId()).isEqualTo(scene.twelveMonths);
        assertThat(result.organizerName()).isEqualTo("극단 열두달");
        assertThat(result.status()).isEqualTo(OwnershipClaimStatus.UNDER_REVIEW);
        assertThat(result.reason()).isEqualTo(CLAIM_REASON);
        assertThat(result.submittedAt()).isEqualTo(SUBMITTED_AT);
        assertThat(result.reviewedAt()).isNull();
        assertThat(result.rejectionReason()).isNull();
        assertThat(result.claimant()).isEqualTo(new OwnershipClaimReviewResult.StaffMember(PLANNER, "푸른바람"));
        assertThat(result.claimantIsOperator()).isTrue();
        assertThat(result.ownerAtSubmission()).isEqualTo(new OwnershipClaimReviewResult.StaffMember(JUNGHOON, "조용한고래"));
        assertThat(result.currentOwner()).isEqualTo(new OwnershipClaimReviewResult.StaffMember(JUNGHOON, "조용한고래"));
    }

    @Test
    @DisplayName("주최자 신청 때 받은 담당자 연락처 · 이메일 · 홈페이지·SNS 링크를 함께 보여 주고, 로그에는 싣지 않는다")
    void showsApplicationContact() {
        OwnershipClaimReviewResult result = review(scene.plannerClaim);

        assertThat(result.applicationContact()).isEqualTo(new OwnershipClaimReviewResult.ApplicationContact(
                scene.approvedApplication, "010-2345-6789", "twelve-months@example.com",
                List.of("https://www.instagram.com/twelve_months_theater")));
        assertThat(result.toString()).doesNotContain("010-2345-6789").doesNotContain("twelve-months@example.com");
        assertThat(result.applicationContact().toString()).doesNotContain("010-2345-6789");
    }

    @Test
    @DisplayName("그 사이 소유자가 바뀌었으면 지금 소유자가 신청 때의 소유자와 다르게 보인다")
    void showsChangedCurrentOwner() {
        OrganizerStaff staff = scene.staffRepository.findByOrganizerId(scene.twelveMonths);
        scene.staffRepository.saveOwnershipTransfer(staff.transferOwnership(JUNGHOON, PROMOTER, NOW));

        OwnershipClaimReviewResult result = review(scene.plannerClaim);

        assertThat(result.ownerAtSubmission().staffAccountId()).isEqualTo(JUNGHOON);
        assertThat(result.currentOwner()).isEqualTo(new OwnershipClaimReviewResult.StaffMember(PROMOTER, "구름"));
    }

    @Test
    @DisplayName("승인한 신청도 결과와 함께 본다 — 신청한 운영자는 이제 소유자라 운영자가 아니다")
    void showsReviewedClaim() {
        scene.approveService().approve(new ApproveOwnershipClaimCommand(admin, scene.plannerClaim));

        OwnershipClaimReviewResult result = review(scene.plannerClaim);

        assertThat(result.status()).isEqualTo(OwnershipClaimStatus.APPROVED);
        assertThat(result.reviewedAt()).isEqualTo(NOW);
        assertThat(result.claimantIsOperator()).isFalse();
        assertThat(result.currentOwner().staffAccountId()).isEqualTo(PLANNER);
    }

    @Test
    @DisplayName("없는 신청이면 404 OWNERSHIP_CLAIM_NOT_FOUND다")
    void unknownClaim() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> review(scene.plannerClaim + 1))
                .satisfies(exception -> assertThat(exception.errorCode())
                        .isEqualTo(OrganizerErrorCode.OWNERSHIP_CLAIM_NOT_FOUND));
    }
}
