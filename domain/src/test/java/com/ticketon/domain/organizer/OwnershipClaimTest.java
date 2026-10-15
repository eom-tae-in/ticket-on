package com.ticketon.domain.organizer;

import static com.ticketon.domain.organizer.OrganizerFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.Nickname;
import com.ticketon.domain.account.PasswordHash;
import com.ticketon.domain.account.StaffAccount;
import com.ticketon.domain.account.StaffAccountStatus;
import com.ticketon.domain.account.StaffPersonalInformation;
import com.ticketon.domain.error.PermissionDeniedException;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달(docs/15 박정훈)의 전 대표가 졸업하고 연락이 끊겨, 운영자인 기획 단원이 관리자에게 소유자 이전을 신청한다(docs/01 "소유자와 연락이
 * 끊긴 경우"). 대표 박정훈(1)이 소유자, 기획 단원(2)과 홍보 단원(3)이 운영자다. 이도현(4)은 다른 주최자의 운영자라 열두달에는 바깥 사람이다.
 */
@DisplayName("소유자 이전 신청 (docs/01 \"소유자 넘기기 — 소유자와 연락이 끊긴 경우\", docs/11 \"대표가 바뀔 때\")")
class OwnershipClaimTest {

    private static final long TWELVE_MONTHS = 12L;
    private static final long JUNGHOON = 1L;
    private static final long PLANNER = 2L;
    private static final long PROMOTER = 3L;
    private static final long DOHYUN = 4L;

    private static final Instant APPROVED_AT = at(2019, 3, 4, 10);
    private static final Instant SUBMITTED_AT = at(2026, 10, 15, 10);
    private static final Instant REVIEWED_AT = at(2026, 10, 16, 15);

    private static final OwnershipClaimReason REASON =
            OwnershipClaimReason.of("전 대표가 졸업한 뒤 전화·메일 모두 답이 없습니다. 올해 정기공연을 준비해야 합니다.");

    private static Instant at(int year, int month, int day, int hour) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, hour, 0));
    }

    /** 대표 박정훈이 소유자, 기획 단원과 홍보 단원이 운영자인 열두달. */
    private static OrganizerStaff twelveMonthsStaff() {
        OrganizerStaff staff = OrganizerStaff.startWithOwner(TWELVE_MONTHS, JUNGHOON, APPROVED_AT);
        staff.addOperator(PLANNER, APPROVED_AT);
        staff.addOperator(PROMOTER, APPROVED_AT);
        return staff;
    }

    private static Organizer twelveMonths(OrganizerStatus status) {
        return Organizer.restore(TWELVE_MONTHS, OrganizerName.of("극단 열두달"), status, OrganizerPublicProfile.empty());
    }

    private static StaffAccount activeAccount(long id) {
        return StaffAccount.restore(id, StaffPersonalInformation.of("김기획", "planner@example.com"), Nickname.of("푸른바람"),
                new PasswordHash("{test}hashed-password"), APPROVED_AT, StaffAccountStatus.ACTIVE, APPROVED_AT);
    }

    /** 기획 단원이 낸, 저장된 심사 중 신청 — 신청 때의 소유자는 박정훈이다. */
    private static OwnershipClaim plannerClaim() {
        return OwnershipClaim.restore(5, TWELVE_MONTHS, PLANNER, JUNGHOON, REASON, SUBMITTED_AT,
                OwnershipClaimStatus.UNDER_REVIEW, null, null);
    }

    @Nested
    @DisplayName("신청")
    class Submit {

        @Test
        @DisplayName("운영자가 사유를 적어 신청하면 심사 중으로 시작하고 그때의 소유자를 가리킨다")
        void operatorSubmits() {
            OwnershipClaim claim = OwnershipClaim.submit(twelveMonthsStaff(), PLANNER, REASON, false, SUBMITTED_AT);

            assertThat(claim.id()).isEmpty();
            assertThat(claim.organizerId()).isEqualTo(TWELVE_MONTHS);
            assertThat(claim.claimantStaffAccountId()).isEqualTo(PLANNER);
            assertThat(claim.ownerStaffAccountId()).isEqualTo(JUNGHOON);
            assertThat(claim.reason()).isEqualTo(REASON);
            assertThat(claim.submittedAt()).isEqualTo(SUBMITTED_AT);
            assertThat(claim.status()).isEqualTo(OwnershipClaimStatus.UNDER_REVIEW);
            assertThat(claim.reviewedAt()).isEmpty();
            assertThat(claim.rejectionReason()).isEmpty();
        }

        @Test
        @DisplayName("소유자 본인은 신청하지 않는다 — 운영자에게 직접 넘긴다 (OWNER_CANNOT_CLAIM_OWNERSHIP)")
        void ownerCannotClaim() {
            assertRejected(() -> OwnershipClaim.submit(twelveMonthsStaff(), JUNGHOON, REASON, false, SUBMITTED_AT),
                    OrganizerErrorCode.OWNER_CANNOT_CLAIM_OWNERSHIP);
            assertThat(OrganizerErrorCode.OWNER_CANNOT_CLAIM_OWNERSHIP.message())
                    .isEqualTo("소유자는 이전 신청 없이 운영자에게 직접 넘길 수 있습니다.");
        }

        @Test
        @DisplayName("그 주최자의 운영진이 아니면(바깥 사람 · 나간 사람) 신청하지 못한다 (NOT_ORGANIZER_STAFF)")
        void outsiderCannotClaim() {
            OrganizerStaff staff = twelveMonthsStaff();
            staff.leave(PROMOTER, SUBMITTED_AT);

            for (long outsider : new long[] {DOHYUN, PROMOTER}) {
                assertThatExceptionOfType(PermissionDeniedException.class)
                        .isThrownBy(() -> OwnershipClaim.submit(staff, outsider, REASON, false, SUBMITTED_AT))
                        .satisfies(exception -> assertThat(exception.errorCode())
                                .isEqualTo(OrganizerErrorCode.NOT_ORGANIZER_STAFF));
            }
        }

        @Test
        @DisplayName("이 주최자에 심사 중인 신청을 이미 냈으면 결과를 기다린다 (OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW)")
        void waitsForPendingClaim() {
            assertRejected(() -> OwnershipClaim.submit(twelveMonthsStaff(), PLANNER, REASON, true, SUBMITTED_AT),
                    OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_UNDER_REVIEW);
        }

        @Test
        @DisplayName("사유는 앞뒤 공백(전각 공백 포함)을 걷고, 비면 받지 않는다")
        void reasonIsRequired() {
            assertThat(OwnershipClaimReason.of("　 연락 두절 　").text()).isEqualTo("연락 두절");
            assertThatIllegalArgumentException().isThrownBy(() -> OwnershipClaimReason.of("　 "));
        }
    }

    @Nested
    @DisplayName("승인")
    class Approve {

        @Test
        @DisplayName("승인하면 신청한 운영자가 소유자가 되고 신청 때의 소유자는 운영자로 남는다 — 소유자는 1명 그대로다")
        void approvalSwapsRoles() {
            OwnershipClaim claim = plannerClaim();
            OrganizerStaff staff = twelveMonthsStaff();

            OwnershipTransfer transfer = claim.approve(twelveMonths(OrganizerStatus.ACTIVE), staff, activeAccount(PLANNER),
                    REVIEWED_AT);

            assertThat(transfer).isEqualTo(new OwnershipTransfer(TWELVE_MONTHS, JUNGHOON, PLANNER, REVIEWED_AT));
            assertThat(staff.currentOwner()).hasValue(PLANNER);
            assertThat(staff.roleOf(JUNGHOON)).contains(StaffRole.OPERATOR);
            assertThat(staff.roleOf(PROMOTER)).contains(StaffRole.OPERATOR);
            assertThat(claim.status()).isEqualTo(OwnershipClaimStatus.APPROVED);
            assertThat(claim.reviewedAt()).contains(REVIEWED_AT);
            assertThat(claim.rejectionReason()).isEmpty();
        }

        @Test
        @DisplayName("이미 처리한 신청은 다시 승인하지 못한다 (OWNERSHIP_CLAIM_ALREADY_REVIEWED)")
        void approvesOnlyOnce() {
            OwnershipClaim claim = plannerClaim();
            claim.reject(RejectionReason.of("소유자와 통화해 직접 넘기기로 함"), REVIEWED_AT);

            assertRejected(() -> claim.approve(twelveMonths(OrganizerStatus.ACTIVE), twelveMonthsStaff(),
                    activeAccount(PLANNER), REVIEWED_AT), OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_REVIEWED);
        }

        @Test
        @DisplayName("신청 뒤 소유자가 이미 바뀌었으면(소유자가 돌아와 직접 넘김) 이 신청으로는 바꾸지 않는다 (OWNERSHIP_ALREADY_TRANSFERRED)")
        void rejectsWhenOwnerAlreadyChanged() {
            OwnershipClaim claim = plannerClaim();
            OrganizerStaff staff = twelveMonthsStaff();
            staff.transferOwnership(JUNGHOON, PROMOTER, SUBMITTED_AT.plusSeconds(3_600));

            assertRejected(() -> claim.approve(twelveMonths(OrganizerStatus.ACTIVE), staff, activeAccount(PLANNER),
                    REVIEWED_AT), OrganizerErrorCode.OWNERSHIP_ALREADY_TRANSFERRED);
            assertThat(staff.currentOwner()).hasValue(PROMOTER);
            assertThat(claim.status()).isEqualTo(OwnershipClaimStatus.UNDER_REVIEW);
        }

        @Test
        @DisplayName("신청한 사람이 그 사이 나갔거나 운영진 로그인을 탈퇴했으면 승인하지 못한다 — 반려한다 (CLAIMANT_NOT_OPERATOR)")
        void rejectsWhenClaimantIsNoLongerOperator() {
            OrganizerStaff leftStaff = twelveMonthsStaff();
            leftStaff.leave(PLANNER, SUBMITTED_AT.plusSeconds(60));
            StaffAccount withdrawn = StaffAccount.restoreWithdrawn(PLANNER, Nickname.of("푸른바람"), APPROVED_AT,
                    SUBMITTED_AT.plusSeconds(60));

            assertRejected(() -> plannerClaim().approve(twelveMonths(OrganizerStatus.ACTIVE), leftStaff,
                    activeAccount(PLANNER), REVIEWED_AT), OrganizerErrorCode.CLAIMANT_NOT_OPERATOR);
            assertRejected(() -> plannerClaim().approve(twelveMonths(OrganizerStatus.ACTIVE), twelveMonthsStaff(),
                    withdrawn, REVIEWED_AT), OrganizerErrorCode.CLAIMANT_NOT_OPERATOR);
            assertThat(OrganizerErrorCode.CLAIMANT_NOT_OPERATOR.message())
                    .isEqualTo("신청한 사람이 이 주최자의 운영자가 아니어서 승인할 수 없습니다. 반려해 주세요.");
        }

        @Test
        @DisplayName("활동 중이 아닌 주최자(닫힘 등)의 신청은 승인하지 못한다 (ORGANIZER_NOT_ACTIVE)")
        void rejectsInactiveOrganizer() {
            assertRejected(() -> plannerClaim().approve(twelveMonths(OrganizerStatus.CLOSED), twelveMonthsStaff(),
                    activeAccount(PLANNER), REVIEWED_AT), OrganizerErrorCode.ORGANIZER_NOT_ACTIVE);
        }

        @Test
        @DisplayName("다른 주최자 · 다른 사람의 계정을 넘기거나, 신청 일시보다 앞서 처리하면 잘못 부른 것이다")
        void rejectsMismatchedArguments() {
            Organizer otherOrganizer = Organizer.restore(13, OrganizerName.of("늦여름"), OrganizerStatus.ACTIVE,
                    OrganizerPublicProfile.empty());

            assertThatIllegalArgumentException().isThrownBy(() -> plannerClaim().approve(otherOrganizer,
                    twelveMonthsStaff(), activeAccount(PLANNER), REVIEWED_AT));
            assertThatIllegalArgumentException().isThrownBy(() -> plannerClaim().approve(
                    twelveMonths(OrganizerStatus.ACTIVE), twelveMonthsStaff(), activeAccount(PROMOTER), REVIEWED_AT));
            assertThatIllegalArgumentException().isThrownBy(() -> plannerClaim().approve(
                    twelveMonths(OrganizerStatus.ACTIVE), twelveMonthsStaff(), activeAccount(PLANNER),
                    SUBMITTED_AT.minusSeconds(1)));
        }
    }

    @Nested
    @DisplayName("반려")
    class Reject {

        @Test
        @DisplayName("사유를 붙여 반려하면 처리 일시와 사유가 남는다")
        void rejectsWithReason() {
            OwnershipClaim claim = plannerClaim();

            claim.reject(RejectionReason.of("소유자와 통화해 직접 넘기기로 함"), REVIEWED_AT);

            assertThat(claim.status()).isEqualTo(OwnershipClaimStatus.REJECTED);
            assertThat(claim.reviewedAt()).contains(REVIEWED_AT);
            assertThat(claim.rejectionReason()).contains(RejectionReason.of("소유자와 통화해 직접 넘기기로 함"));
        }

        @Test
        @DisplayName("이미 처리한 신청은 다시 반려하지 못한다 (OWNERSHIP_CLAIM_ALREADY_REVIEWED)")
        void rejectsOnlyOnce() {
            OwnershipClaim claim = plannerClaim();
            claim.approve(twelveMonths(OrganizerStatus.ACTIVE), twelveMonthsStaff(), activeAccount(PLANNER), REVIEWED_AT);

            assertRejected(() -> claim.reject(RejectionReason.of("정보가 부족함"), REVIEWED_AT),
                    OrganizerErrorCode.OWNERSHIP_CLAIM_ALREADY_REVIEWED);
            assertThat(claim.status()).isEqualTo(OwnershipClaimStatus.APPROVED);
        }
    }

    @Nested
    @DisplayName("저장소에서 되살리기")
    class Restore {

        @Test
        @DisplayName("상태와 처리 일시 · 반려 사유가 맞아야 한다 — 반려인데 사유가 없거나, 심사 중인데 처리 일시가 있으면 되살리지 않는다")
        void reviewMustMatchStatus() {
            RejectionReason reason = RejectionReason.of("정보가 부족함");

            assertThat(OwnershipClaim.restore(5, TWELVE_MONTHS, PLANNER, JUNGHOON, REASON, SUBMITTED_AT,
                    OwnershipClaimStatus.REJECTED, REVIEWED_AT, reason).rejectionReason()).contains(reason);
            assertThatIllegalArgumentException().isThrownBy(() -> OwnershipClaim.restore(5, TWELVE_MONTHS, PLANNER,
                    JUNGHOON, REASON, SUBMITTED_AT, OwnershipClaimStatus.REJECTED, REVIEWED_AT, null));
            assertThatIllegalArgumentException().isThrownBy(() -> OwnershipClaim.restore(5, TWELVE_MONTHS, PLANNER,
                    JUNGHOON, REASON, SUBMITTED_AT, OwnershipClaimStatus.UNDER_REVIEW, REVIEWED_AT, null));
            assertThatIllegalArgumentException().isThrownBy(() -> OwnershipClaim.restore(5, TWELVE_MONTHS, PLANNER,
                    JUNGHOON, REASON, SUBMITTED_AT, OwnershipClaimStatus.APPROVED, REVIEWED_AT, reason));
        }

        @Test
        @DisplayName("id는 1 이상, 신청한 사람과 신청 때의 소유자는 다른 사람, 처리는 신청 뒤여야 한다")
        void restoreRules() {
            assertThatIllegalArgumentException().isThrownBy(() -> OwnershipClaim.restore(0, TWELVE_MONTHS, PLANNER,
                    JUNGHOON, REASON, SUBMITTED_AT, OwnershipClaimStatus.UNDER_REVIEW, null, null));
            assertThatIllegalArgumentException().isThrownBy(() -> OwnershipClaim.restore(5, TWELVE_MONTHS, JUNGHOON,
                    JUNGHOON, REASON, SUBMITTED_AT, OwnershipClaimStatus.UNDER_REVIEW, null, null));
            assertThatIllegalArgumentException().isThrownBy(() -> OwnershipClaim.restore(5, TWELVE_MONTHS, PLANNER,
                    JUNGHOON, REASON, SUBMITTED_AT, OwnershipClaimStatus.APPROVED, SUBMITTED_AT.minusSeconds(1), null));
        }
    }
}
