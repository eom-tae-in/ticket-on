package com.ticketon.application.admin.service;

import static com.ticketon.application.admin.service.OrganizerChangeRequestScene.DOHYUN;
import static com.ticketon.application.admin.service.OrganizerChangeRequestScene.NOW;
import static com.ticketon.application.admin.service.OrganizerChangeRequestScene.REQUESTED_AT;
import static com.ticketon.application.admin.service.OrganizerChangeRequestScene.YEOREUM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.admin.port.in.ApproveOrganizerChangeRequestCommand;
import com.ticketon.application.admin.port.in.GetOrganizerChangeRequestReviewQuery;
import com.ticketon.application.admin.port.in.OrganizerChangeRequestReviewResult;
import com.ticketon.application.admin.port.in.OrganizerChangeRequestReviewResult.BusinessInformation;
import com.ticketon.application.admin.port.in.OrganizerChangeRequestReviewResult.SettlementAccount;
import com.ticketon.application.admin.port.in.OrganizerChangeRequestReviewResult.StaffMember;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.account.AdminGrade;
import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.organizer.OrganizerChangeRequestKind;
import com.ticketon.domain.organizer.OrganizerChangeRequestStatus;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 관리자가 처리 목록의 '정산 계좌 · 사업자 정보 변경 확인'을 열어 본다({@link OrganizerChangeRequestScene}). */
@DisplayName("정산 계좌 · 사업자 정보 변경 확인 화면 유스케이스")
class GetOrganizerChangeRequestReviewServiceTest {

    private final OrganizerChangeRequestScene scene = new OrganizerChangeRequestScene();
    private final GetOrganizerChangeRequestReviewService service = scene.reviewService();
    private final AccountRef admin = scene.storeAdmin(AdminGrade.ADMIN);

    private OrganizerChangeRequestReviewResult review(long requestId) {
        return service.getReview(new GetOrganizerChangeRequestReviewQuery(admin, requestId));
    }

    @Test
    @DisplayName("정산 계좌 변경은 지금 계좌와 요청한 계좌를 가리지 않고 나란히 보이고, 요청한 사람 · 지금 소유자를 함께 보인다")
    void showsSettlementAccountChange() {
        OrganizerChangeRequestReviewResult result = review(scene.accountRequest);

        assertThat(result.changeRequestId()).isEqualTo(scene.accountRequest);
        assertThat(result.organizerId()).isEqualTo(scene.lateSummer);
        assertThat(result.organizerName()).isEqualTo("늦여름");
        assertThat(result.kind()).isEqualTo(OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT);
        assertThat(result.status()).isEqualTo(OrganizerChangeRequestStatus.UNDER_REVIEW);
        assertThat(result.requestedAt()).isEqualTo(REQUESTED_AT);
        assertThat(result.reviewedAt()).isNull();
        assertThat(result.rejectionReason()).isNull();
        assertThat(result.requester()).isEqualTo(new StaffMember(YEOREUM, "늦은여름"));
        assertThat(result.requesterIsOwner()).isTrue();
        assertThat(result.currentOwner()).isEqualTo(new StaffMember(YEOREUM, "늦은여름"));
        assertThat(result.currentSettlementAccount()).isEqualTo(new SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드"));
        assertThat(result.requestedSettlementAccount()).isEqualTo(new SettlementAccount("우리은행", "1002-345-678901", "최여름"));
        assertThat(result.currentBusinessInformation()).isNull();
        assertThat(result.requestedBusinessInformation()).isNull();
        assertThat(result.toString()).doesNotContain("110-123-456789", "1002-345-678901");
    }

    @Test
    @DisplayName("사업자 정보 변경은 지금 사업자 정보와 요청한 사업자 정보를 나란히 보인다 — 정산 계좌 칸은 비어 있다")
    void showsBusinessInformationChange() {
        OrganizerChangeRequestReviewResult result = review(scene.businessRequest);

        assertThat(result.kind()).isEqualTo(OrganizerChangeRequestKind.BUSINESS_INFORMATION);
        assertThat(result.currentBusinessInformation()).isEqualTo(new BusinessInformation("늦여름 레코드", "최여름",
                "서울특별시 마포구 와우산로 12", "02-123-4567", "1234567890"));
        assertThat(result.requestedBusinessInformation()).isEqualTo(new BusinessInformation("늦여름 레코드", "최여름",
                "서울특별시 마포구 독막로 7", "02-765-4321", "1234567890"));
        assertThat(result.currentSettlementAccount()).isNull();
        assertThat(result.requestedSettlementAccount()).isNull();
    }

    @Test
    @DisplayName("요청한 뒤 소유자를 넘겼으면 '요청한 사람이 지금 소유자 아님'과 지금 소유자를 보인다 — 승인할 수 없고 반려한다")
    void showsRequesterIsNoLongerOwner() {
        scene.transferOwnershipToDohyun();

        OrganizerChangeRequestReviewResult result = review(scene.accountRequest);

        assertThat(result.requesterIsOwner()).isFalse();
        assertThat(result.currentOwner()).isEqualTo(new StaffMember(DOHYUN, "밤의기타"));
    }

    @Test
    @DisplayName("이미 처리한 요청도 결과와 함께 본다")
    void showsReviewedRequest() {
        scene.approveService().approve(new ApproveOrganizerChangeRequestCommand(admin, scene.accountRequest));

        OrganizerChangeRequestReviewResult result = review(scene.accountRequest);

        assertThat(result.status()).isEqualTo(OrganizerChangeRequestStatus.APPROVED);
        assertThat(result.reviewedAt()).isEqualTo(NOW);
        assertThat(result.currentSettlementAccount()).isEqualTo(result.requestedSettlementAccount());
    }

    @Test
    @DisplayName("없는 요청이면 404 ORGANIZER_CHANGE_REQUEST_NOT_FOUND")
    void unknownRequest() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> review(scene.accountRequest + 100))
                .satisfies(notFound -> assertThat(notFound.errorCode())
                        .isEqualTo(OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_NOT_FOUND));
    }
}
