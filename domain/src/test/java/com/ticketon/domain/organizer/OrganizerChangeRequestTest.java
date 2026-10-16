package com.ticketon.domain.organizer;

import static com.ticketon.domain.organizer.OrganizerFixtures.assertRejected;
import static com.ticketon.domain.organizer.OrganizerFixtures.lateSummerBusinessInformation;
import static com.ticketon.domain.organizer.OrganizerFixtures.twelveMonthsSettlementAccount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AdminAction;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 극단 열두달(비사업자, docs/15 박정훈)의 대표 박정훈(1)이 정산 계좌를 국민은행에서 우리은행으로 바꾸려 한다. 늦여름(사업자)의 대표 최여름(3)은
 * 사업장 주소를 옮겨 사업자 정보를 바꾸려 한다. 기획 단원(2)은 열두달의 운영자다. 관리자(7)가 확인한 뒤에야 적용된다
 * (docs/01 "가로채기를 막는 장치", docs/11 "정산 계좌 변경").
 */
@DisplayName("정산 계좌 · 사업자 정보 변경 요청 (docs/11 \"정산 계좌 변경\", docs/01 \"판매자 정보\")")
class OrganizerChangeRequestTest {

    private static final long TWELVE_MONTHS = 12L;
    private static final long LATE_SUMMER = 13L;
    private static final long JUNGHOON = 1L;
    private static final long PLANNER = 2L;
    private static final long YEOREUM = 3L;
    private static final long ADMIN = 7L;

    private static final Instant APPROVED_AT = at(2019, 3, 4, 10);
    private static final Instant REQUESTED_AT = at(2026, 10, 15, 10);
    private static final Instant REVIEWED_AT = at(2026, 10, 16, 15);

    private static final SettlementAccount WOORI = new SettlementAccount("우리은행", "1002-345-678901", "박정훈");

    private static final BusinessInformation MOVED_BUSINESS = new BusinessInformation("늦여름 레코드", "최여름",
            "서울특별시 마포구 독막로 7", TelephoneNumber.of("02-765-4321"), BusinessRegistrationNumber.of("123-45-67890"));

    private static final RejectionReason REASON = RejectionReason.of("예금주가 대표자 이름과 다릅니다. 통장 사본을 보내 주세요.");

    private static Instant at(int year, int month, int day, int hour) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, 0));
    }

    /** 열두달의 지금 값 — 비사업자, 국민은행 계좌. */
    private static OrganizerBusinessDetails twelveMonthsDetails() {
        return new OrganizerBusinessDetails(BusinessDeclaration.nonBusiness(), twelveMonthsSettlementAccount());
    }

    /** 늦여름의 지금 값 — 사업자, 신한은행 계좌. */
    private static OrganizerBusinessDetails lateSummerDetails() {
        return new OrganizerBusinessDetails(BusinessDeclaration.business(lateSummerBusinessInformation()),
                new SettlementAccount("신한은행", "110-123-456789", "늦여름 레코드"));
    }

    private static Organizer twelveMonths(OrganizerStatus status) {
        return Organizer.restore(TWELVE_MONTHS, OrganizerName.of("극단 열두달"), status, OrganizerPublicProfile.empty());
    }

    /** 박정훈이 소유자, 기획 단원이 운영자인 열두달 명단. */
    private static OrganizerStaff twelveMonthsStaff() {
        OrganizerStaff staff = OrganizerStaff.startWithOwner(TWELVE_MONTHS, JUNGHOON, APPROVED_AT);
        staff.addOperator(PLANNER, APPROVED_AT);
        return staff;
    }

    /** 박정훈이 낸, 저장된 확인 중 정산 계좌 변경 요청. */
    private static OrganizerChangeRequest accountRequest() {
        return OrganizerChangeRequest.restore(5, TWELVE_MONTHS, JUNGHOON,
                new RequestedOrganizerChange.NewSettlementAccount(WOORI), REQUESTED_AT,
                OrganizerChangeRequestStatus.UNDER_REVIEW, null, null, null);
    }

    @Nested
    @DisplayName("요청")
    class Request {

        @Test
        @DisplayName("소유자가 새 정산 계좌를 요청하면 확인 중으로 시작하고, 지금 값은 건드리지 않는다")
        void ownerRequestsSettlementAccountChange() {
            OrganizerBusinessDetails current = twelveMonthsDetails();

            OrganizerChangeRequest request = OrganizerChangeRequest.request(TWELVE_MONTHS, JUNGHOON, current,
                    new RequestedOrganizerChange.NewSettlementAccount(WOORI), false, REQUESTED_AT);

            assertThat(request.id()).isEmpty();
            assertThat(request.organizerId()).isEqualTo(TWELVE_MONTHS);
            assertThat(request.requesterStaffAccountId()).isEqualTo(JUNGHOON);
            assertThat(request.kind()).isEqualTo(OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT);
            assertThat(request.requestedChange()).isEqualTo(new RequestedOrganizerChange.NewSettlementAccount(WOORI));
            assertThat(request.requestedAt()).isEqualTo(REQUESTED_AT);
            assertThat(request.status()).isEqualTo(OrganizerChangeRequestStatus.UNDER_REVIEW);
            assertThat(request.reviewedAt()).isEmpty();
            assertThat(request.reviewerAdminAccountId()).isEmpty();
            assertThat(request.rejectionReason()).isEmpty();
            assertThat(current.settlementAccount()).isEqualTo(twelveMonthsSettlementAccount());
        }

        @Test
        @DisplayName("사업자 주최자는 새 사업자 정보 다섯 가지를 요청한다")
        void businessOrganizerRequestsBusinessInformationChange() {
            OrganizerChangeRequest request = OrganizerChangeRequest.request(LATE_SUMMER, YEOREUM, lateSummerDetails(),
                    new RequestedOrganizerChange.NewBusinessInformation(MOVED_BUSINESS), false, REQUESTED_AT);

            assertThat(request.kind()).isEqualTo(OrganizerChangeRequestKind.BUSINESS_INFORMATION);
            assertThat(request.requestedChange())
                    .isEqualTo(new RequestedOrganizerChange.NewBusinessInformation(MOVED_BUSINESS));
        }

        @Test
        @DisplayName("비사업자는 사업자 정보 변경을 요청하지 못한다 (NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION) — 사업자 여부를 바꾸는 일은 문서에 없다")
        void nonBusinessCannotRequestBusinessInformationChange() {
            assertRejected(() -> OrganizerChangeRequest.request(TWELVE_MONTHS, JUNGHOON, twelveMonthsDetails(),
                            new RequestedOrganizerChange.NewBusinessInformation(MOVED_BUSINESS), true, REQUESTED_AT),
                    OrganizerErrorCode.NON_BUSINESS_ORGANIZER_HAS_NO_BUSINESS_INFORMATION);
        }

        @Test
        @DisplayName("같은 종류의 요청이 확인 중이면 결과를 기다린다 (ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW)")
        void sameKindUnderReviewWaits() {
            assertRejected(() -> OrganizerChangeRequest.request(TWELVE_MONTHS, JUNGHOON, twelveMonthsDetails(),
                            new RequestedOrganizerChange.NewSettlementAccount(WOORI), true, REQUESTED_AT),
                    OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_UNDER_REVIEW);
        }

        @Test
        @DisplayName("지금 값과 같으면(앞뒤 공백만 다름) 요청하지 않는다 (ORGANIZER_CHANGE_SAME_AS_CURRENT)")
        void sameAsCurrentIsRejected() {
            SettlementAccount sameAccount = new SettlementAccount(" 국민은행 ", "123456-78-901234 ", "박정훈");

            assertRejected(() -> OrganizerChangeRequest.request(TWELVE_MONTHS, JUNGHOON, twelveMonthsDetails(),
                            new RequestedOrganizerChange.NewSettlementAccount(sameAccount), false, REQUESTED_AT),
                    OrganizerErrorCode.ORGANIZER_CHANGE_SAME_AS_CURRENT);
            assertRejected(() -> OrganizerChangeRequest.request(LATE_SUMMER, YEOREUM, lateSummerDetails(),
                            new RequestedOrganizerChange.NewBusinessInformation(lateSummerBusinessInformation()), false,
                            REQUESTED_AT),
                    OrganizerErrorCode.ORGANIZER_CHANGE_SAME_AS_CURRENT);
        }
    }

    @Nested
    @DisplayName("승인")
    class Approve {

        @Test
        @DisplayName("승인하면 지금 값에 새 정산 계좌를 적용한 값을 돌려주고, 요청은 승인 · 처리 일시 · 처리한 관리자를 갖는다")
        void approvesAndAppliesNewAccount() {
            OrganizerChangeRequest request = accountRequest();

            OrganizerBusinessDetails changed = request.approve(twelveMonths(OrganizerStatus.ACTIVE), twelveMonthsStaff(),
                    twelveMonthsDetails(), ADMIN, REVIEWED_AT);

            assertThat(changed).isEqualTo(new OrganizerBusinessDetails(BusinessDeclaration.nonBusiness(), WOORI));
            assertThat(request.status()).isEqualTo(OrganizerChangeRequestStatus.APPROVED);
            assertThat(request.reviewedAt()).contains(REVIEWED_AT);
            assertThat(request.reviewerAdminAccountId()).hasValue(ADMIN);
            assertThat(request.rejectionReason()).isEmpty();
        }

        @Test
        @DisplayName("승인할 때의 값에 적용한다 — 그 사이 승인된 사업자 정보 변경은 그대로 남는다")
        void appliesToValuesAtApproval() {
            OrganizerStaff staff = OrganizerStaff.startWithOwner(LATE_SUMMER, YEOREUM, APPROVED_AT);
            Organizer lateSummer = Organizer.restore(LATE_SUMMER, OrganizerName.of("늦여름"), OrganizerStatus.ACTIVE,
                    OrganizerPublicProfile.empty());
            OrganizerChangeRequest accountChange = OrganizerChangeRequest.restore(6, LATE_SUMMER, YEOREUM,
                    new RequestedOrganizerChange.NewSettlementAccount(WOORI), REQUESTED_AT,
                    OrganizerChangeRequestStatus.UNDER_REVIEW, null, null, null);
            OrganizerBusinessDetails businessAlreadyMoved = lateSummerDetails().withBusinessInformation(MOVED_BUSINESS);

            OrganizerBusinessDetails changed = accountChange.approve(lateSummer, staff, businessAlreadyMoved, ADMIN,
                    REVIEWED_AT);

            assertThat(changed.businessInformation()).contains(MOVED_BUSINESS);
            assertThat(changed.settlementAccount()).isEqualTo(WOORI);
        }

        @Test
        @DisplayName("이미 승인 · 반려한 요청은 다시 처리하지 못한다 (ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED)")
        void cannotReviewTwice() {
            OrganizerChangeRequest approved = accountRequest();
            approved.approve(twelveMonths(OrganizerStatus.ACTIVE), twelveMonthsStaff(), twelveMonthsDetails(), ADMIN,
                    REVIEWED_AT);
            OrganizerChangeRequest rejected = accountRequest();
            rejected.reject(REASON, ADMIN, REVIEWED_AT);

            assertRejected(() -> approved.approve(twelveMonths(OrganizerStatus.ACTIVE), twelveMonthsStaff(),
                    twelveMonthsDetails(), ADMIN, REVIEWED_AT), OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED);
            assertRejected(() -> rejected.approve(twelveMonths(OrganizerStatus.ACTIVE), twelveMonthsStaff(),
                    twelveMonthsDetails(), ADMIN, REVIEWED_AT), OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED);
            assertRejected(() -> approved.reject(REASON, ADMIN, REVIEWED_AT),
                    OrganizerErrorCode.ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = OrganizerStatus.class, names = "ACTIVE", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("활동 중인 주최자가 아니면 승인하지 못한다 (ORGANIZER_NOT_ACTIVE) — 요청은 확인 중으로 남는다")
        void organizerMustBeActive(OrganizerStatus status) {
            OrganizerChangeRequest request = accountRequest();

            assertRejected(() -> request.approve(twelveMonths(status), twelveMonthsStaff(), twelveMonthsDetails(), ADMIN,
                    REVIEWED_AT), OrganizerErrorCode.ORGANIZER_NOT_ACTIVE);
            assertThat(request.status()).isEqualTo(OrganizerChangeRequestStatus.UNDER_REVIEW);
        }

        @Test
        @DisplayName("요청한 뒤 소유자를 넘겼으면 승인하지 못한다 (CHANGE_REQUESTER_NOT_OWNER) — 넘겨받은 사람이 모르는 계좌로 정산금이 가지 않게")
        void requesterMustStillBeOwner() {
            OrganizerStaff staff = twelveMonthsStaff();
            staff.transferOwnership(JUNGHOON, PLANNER, REQUESTED_AT.plusSeconds(3_600));
            OrganizerChangeRequest request = accountRequest();

            assertRejected(() -> request.approve(twelveMonths(OrganizerStatus.ACTIVE), staff, twelveMonthsDetails(), ADMIN,
                    REVIEWED_AT), OrganizerErrorCode.CHANGE_REQUESTER_NOT_OWNER);
            assertThat(request.status()).isEqualTo(OrganizerChangeRequestStatus.UNDER_REVIEW);
        }

        @Test
        @DisplayName("다른 주최자 · 명단을 넘기거나 요청 일시보다 앞선 시각이면 잘못 부른 것이다")
        void rejectsWrongArguments() {
            OrganizerChangeRequest request = accountRequest();
            Organizer lateSummer = Organizer.restore(LATE_SUMMER, OrganizerName.of("늦여름"), OrganizerStatus.ACTIVE,
                    OrganizerPublicProfile.empty());

            assertThatIllegalArgumentException().isThrownBy(() -> request.approve(lateSummer, twelveMonthsStaff(),
                    twelveMonthsDetails(), ADMIN, REVIEWED_AT));
            assertThatIllegalArgumentException().isThrownBy(() -> request.approve(twelveMonths(OrganizerStatus.ACTIVE),
                    OrganizerStaff.startWithOwner(LATE_SUMMER, YEOREUM, APPROVED_AT), twelveMonthsDetails(), ADMIN,
                    REVIEWED_AT));
            assertThatIllegalArgumentException().isThrownBy(() -> request.approve(twelveMonths(OrganizerStatus.ACTIVE),
                    twelveMonthsStaff(), twelveMonthsDetails(), ADMIN, REQUESTED_AT.minusSeconds(1)));
        }
    }

    @Nested
    @DisplayName("반려")
    class Reject {

        @Test
        @DisplayName("사유를 붙여 반려하면 반려 · 처리 일시 · 처리한 관리자 · 사유를 갖는다")
        void rejectsWithReason() {
            OrganizerChangeRequest request = accountRequest();

            request.reject(REASON, ADMIN, REVIEWED_AT);

            assertThat(request.status()).isEqualTo(OrganizerChangeRequestStatus.REJECTED);
            assertThat(request.reviewedAt()).contains(REVIEWED_AT);
            assertThat(request.reviewerAdminAccountId()).hasValue(ADMIN);
            assertThat(request.rejectionReason()).contains(REASON);
        }

        @Test
        @DisplayName("요청 일시보다 앞서 반려할 수 없다")
        void cannotRejectBeforeRequest() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> accountRequest().reject(REASON, ADMIN, REQUESTED_AT.minusSeconds(1)));
        }
    }

    @Nested
    @DisplayName("되살리기")
    class Restore {

        @Test
        @DisplayName("상태와 처리 일시 · 처리한 관리자 · 반려 사유가 맞지 않으면 되살리지 않는다")
        void rejectsMismatchedReview() {
            RequestedOrganizerChange change = new RequestedOrganizerChange.NewSettlementAccount(WOORI);

            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerChangeRequest.restore(5, TWELVE_MONTHS,
                    JUNGHOON, change, REQUESTED_AT, OrganizerChangeRequestStatus.UNDER_REVIEW, REVIEWED_AT, ADMIN, null));
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerChangeRequest.restore(5, TWELVE_MONTHS,
                    JUNGHOON, change, REQUESTED_AT, OrganizerChangeRequestStatus.APPROVED, REVIEWED_AT, null, null));
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerChangeRequest.restore(5, TWELVE_MONTHS,
                    JUNGHOON, change, REQUESTED_AT, OrganizerChangeRequestStatus.REJECTED, REVIEWED_AT, ADMIN, null));
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerChangeRequest.restore(5, TWELVE_MONTHS,
                    JUNGHOON, change, REQUESTED_AT, OrganizerChangeRequestStatus.APPROVED, REQUESTED_AT.minusSeconds(1),
                    ADMIN, null));
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerChangeRequest.restore(0, TWELVE_MONTHS,
                    JUNGHOON, change, REQUESTED_AT, OrganizerChangeRequestStatus.UNDER_REVIEW, null, null, null));
        }

        @Test
        @DisplayName("처리한 요청을 그대로 되살린다")
        void restoresReviewed() {
            OrganizerChangeRequest rejected = OrganizerChangeRequest.restore(5, TWELVE_MONTHS, JUNGHOON,
                    new RequestedOrganizerChange.NewSettlementAccount(WOORI), REQUESTED_AT,
                    OrganizerChangeRequestStatus.REJECTED, REVIEWED_AT, ADMIN, REASON);

            assertThat(rejected.id()).hasValue(5);
            assertThat(rejected.rejectionReason()).contains(REASON);
            assertThat(rejected.reviewerAdminAccountId()).hasValue(ADMIN);
        }
    }

    @Nested
    @DisplayName("종류")
    class Kind {

        @ParameterizedTest(name = "{0}")
        @EnumSource(OrganizerChangeRequestKind.class)
        @DisplayName("정산 계좌 · 사업자 정보 변경 요청은 소유자만 한다 (docs/01 \"권한 두 가지\", docs/11 \"(소유자만)\")")
        void onlyOwnerRequests(OrganizerChangeRequestKind kind) {
            assertThat(kind.staffAction().isOwnerOnly()).isTrue();
            assertThat(StaffRole.OPERATOR.can(kind.staffAction())).isFalse();
            assertThat(StaffRole.OWNER.can(kind.staffAction())).isTrue();
        }

        @Test
        @DisplayName("관리자가 확인하는 일은 관리자 2등급 표의 '정산 계좌 · 사업자 정보 변경 확인'이다 — 두 등급 모두")
        void adminActions() {
            assertThat(OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT.adminAction())
                    .isEqualTo(AdminAction.CONFIRM_SETTLEMENT_ACCOUNT_CHANGE);
            assertThat(OrganizerChangeRequestKind.BUSINESS_INFORMATION.adminAction())
                    .isEqualTo(AdminAction.CONFIRM_BUSINESS_INFORMATION_CHANGE);
            assertThat(OrganizerChangeRequestKind.SETTLEMENT_ACCOUNT.adminAction().isSuperAdminOnly()).isFalse();
            assertThat(OrganizerChangeRequestKind.BUSINESS_INFORMATION.adminAction().isSuperAdminOnly()).isFalse();
        }

        @Test
        @DisplayName("새 값은 로그에 싣지 않는다")
        void hidesValuesInToString() {
            assertThat(new RequestedOrganizerChange.NewSettlementAccount(WOORI).toString())
                    .doesNotContain("1002-345-678901", "우리은행");
            assertThat(new RequestedOrganizerChange.NewBusinessInformation(MOVED_BUSINESS).toString())
                    .doesNotContain("1234567890", "독막로");
        }
    }
}
