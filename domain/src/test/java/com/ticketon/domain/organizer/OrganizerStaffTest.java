package com.ticketon.domain.organizer;

import static com.ticketon.domain.organizer.OrganizerFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.ticketon.common.KoreanTime;
import com.ticketon.domain.error.PermissionDeniedException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 극단 열두달(docs/15 박정훈)의 운영진 — 대표 박정훈이 소유자, 기획 담당 단원이 운영자. 이도현은 다른 주최자(늦여름)의 운영자라 열두달에는
 * 바깥 사람이다.
 */
@DisplayName("주최자 운영진 명단 (docs/01 \"주최자와 운영진\")")
class OrganizerStaffTest {

    private static final long TWELVE_MONTHS = 12L;
    private static final long JUNGHOON = 1L;
    private static final long PLANNER = 2L;
    private static final long DOHYUN = 4L;

    private static final Instant APPROVED_AT = at(2019, 3, 4);
    private static final Instant PLANNER_JOINED_AT = at(2019, 5, 1);
    private static final Instant TRANSFERRED_AT = at(2026, 3, 2);
    private static final Instant LATER = at(2026, 9, 1);

    private static Instant at(int year, int month, int day) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, 10, 0));
    }

    /** 승인 뒤 기획 단원이 운영자로 들어온 열두달. */
    private static OrganizerStaff twelveMonthsStaff() {
        OrganizerStaff staff = OrganizerStaff.startWithOwner(TWELVE_MONTHS, JUNGHOON, APPROVED_AT);
        staff.addOperator(PLANNER, PLANNER_JOINED_AT);
        return staff;
    }

    private static void assertPermissionDenied(ThrowingCallable action, OrganizerErrorCode expectedCode) {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    private static long activeOwnerCount(OrganizerStaff staff) {
        return staff.activeMemberships().stream().filter(membership -> membership.role() == StaffRole.OWNER).count();
    }

    @Nested
    @DisplayName("승인")
    class Approval {

        @Test
        @DisplayName("신청한 사람이 소유자가 된다 — 소유자 1명, 승인 일시부터")
        void applicantBecomesOwner() {
            OrganizerStaff staff = OrganizerStaff.startWithOwner(TWELVE_MONTHS, JUNGHOON, APPROVED_AT);

            assertThat(staff.currentOwner()).hasValue(JUNGHOON);
            assertThat(staff.roleOf(JUNGHOON)).contains(StaffRole.OWNER);
            assertThat(staff.memberships()).containsExactly(
                    new StaffMembership(TWELVE_MONTHS, JUNGHOON, StaffRole.OWNER, APPROVED_AT, null));
        }
    }

    @Nested
    @DisplayName("권한 확인")
    class Permission {

        @ParameterizedTest(name = "{0}")
        @DisplayName("소유자는 모든 일을 할 수 있다")
        @EnumSource(StaffAction.class)
        void ownerIsPermitted(StaffAction action) {
            assertThatNoException().isThrownBy(() -> twelveMonthsStaff().requirePermission(JUNGHOON, action));
        }

        @ParameterizedTest(name = "{0}")
        @DisplayName("운영자는 소유자만 하는 일에서 '소유자만 할 수 있는 일입니다'로 막힌다")
        @EnumSource(StaffAction.class)
        void operatorFollowsTable(StaffAction action) {
            OrganizerStaff staff = twelveMonthsStaff();

            if (action.isOwnerOnly()) {
                assertPermissionDenied(() -> staff.requirePermission(PLANNER, action), OrganizerErrorCode.OWNER_ONLY_ACTION);
            } else {
                assertThatNoException().isThrownBy(() -> staff.requirePermission(PLANNER, action));
            }
        }

        @Test
        @DisplayName("이 주최자에 소속되지 않은 사람은 운영진 공통의 일도 막힌다")
        void outsiderIsDenied() {
            assertPermissionDenied(() -> twelveMonthsStaff().requirePermission(DOHYUN, StaffAction.VIEW_STATUS),
                    OrganizerErrorCode.NOT_ORGANIZER_STAFF);
        }

        @Test
        @DisplayName("나간 사람은 그 주최자의 정산·현황을 볼 수 없다 (docs/01 \"지난 소속\")")
        void formerStaffIsDenied() {
            OrganizerStaff staff = twelveMonthsStaff();
            staff.leave(PLANNER, LATER);

            assertPermissionDenied(() -> staff.requirePermission(PLANNER, StaffAction.VIEW_SETTLEMENT),
                    OrganizerErrorCode.NOT_ORGANIZER_STAFF);
        }
    }

    @Nested
    @DisplayName("운영자 들이기")
    class AddOperator {

        @Test
        @DisplayName("초대를 수락한 사람이 운영자가 된다 — 운영자는 여러 명일 수 있다")
        void addsOperators() {
            OrganizerStaff staff = twelveMonthsStaff();

            staff.addOperator(DOHYUN, LATER);

            assertThat(staff.roleOf(PLANNER)).contains(StaffRole.OPERATOR);
            assertThat(staff.roleOf(DOHYUN)).contains(StaffRole.OPERATOR);
            assertThat(activeOwnerCount(staff)).isEqualTo(1);
        }

        @Test
        @DisplayName("이미 소속된 사람은 다시 들일 수 없다 — 소유자도 운영자로 겹쳐 들어갈 수 없다")
        void cannotAddExistingStaff() {
            OrganizerStaff staff = twelveMonthsStaff();

            assertRejected(() -> staff.addOperator(PLANNER, LATER), OrganizerErrorCode.ALREADY_ORGANIZER_STAFF);
            assertRejected(() -> staff.addOperator(JUNGHOON, LATER), OrganizerErrorCode.ALREADY_ORGANIZER_STAFF);
        }

        @Test
        @DisplayName("나갔던 사람이 다시 들어오면 새 소속으로 시작하고 지난 소속은 남는다")
        void rejoinKeepsHistory() {
            OrganizerStaff staff = twelveMonthsStaff();
            staff.leave(PLANNER, TRANSFERRED_AT);

            staff.addOperator(PLANNER, LATER);

            assertThat(staff.memberships()).contains(
                    new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, PLANNER_JOINED_AT, TRANSFERRED_AT),
                    new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, LATER, null));
        }

        @Test
        @DisplayName("운영진이 아무도 남지 않은 명단(닫힘·영구 정지)에는 들일 수 없다")
        void cannotAddToDisbandedStaff() {
            OrganizerStaff disbanded = OrganizerStaff.restore(TWELVE_MONTHS, List.of(
                    new StaffMembership(TWELVE_MONTHS, JUNGHOON, StaffRole.OWNER, APPROVED_AT, LATER)));

            assertThatIllegalStateException().isThrownBy(() -> disbanded.addOperator(PLANNER, LATER));
        }
    }

    @Nested
    @DisplayName("소유자 넘기기")
    class TransferOwnership {

        @Test
        @DisplayName("운영자에게 넘기면 받은 사람이 소유자가 되고 넘긴 사람은 운영자로 남는다")
        void transfersToOperator() {
            OrganizerStaff staff = twelveMonthsStaff();

            OwnershipTransfer transfer = staff.transferOwnership(JUNGHOON, PLANNER, TRANSFERRED_AT);

            assertThat(transfer).isEqualTo(new OwnershipTransfer(TWELVE_MONTHS, JUNGHOON, PLANNER, TRANSFERRED_AT));
            assertThat(staff.currentOwner()).hasValue(PLANNER);
            assertThat(staff.roleOf(PLANNER)).contains(StaffRole.OWNER);
            assertThat(staff.roleOf(JUNGHOON)).contains(StaffRole.OPERATOR);
        }

        @Test
        @DisplayName("소유자는 넘기기 전에도 뒤에도 1명이다")
        void keepsSingleOwner() {
            OrganizerStaff staff = twelveMonthsStaff();
            assertThat(activeOwnerCount(staff)).isEqualTo(1);

            staff.transferOwnership(JUNGHOON, PLANNER, TRANSFERRED_AT);

            assertThat(activeOwnerCount(staff)).isEqualTo(1);
            assertThat(staff.activeMemberships()).hasSize(2);
        }

        @Test
        @DisplayName("역할이 바뀐 기간이 이력으로 남는다 — 두 사람 모두 그때까지의 소속을 끝내고 같은 시각에 새 역할로 시작한다")
        void recordsRoleChangeAsHistory() {
            OrganizerStaff staff = twelveMonthsStaff();

            staff.transferOwnership(JUNGHOON, PLANNER, TRANSFERRED_AT);

            assertThat(staff.memberships()).containsExactlyInAnyOrder(
                    new StaffMembership(TWELVE_MONTHS, JUNGHOON, StaffRole.OWNER, APPROVED_AT, TRANSFERRED_AT),
                    new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, PLANNER_JOINED_AT, TRANSFERRED_AT),
                    new StaffMembership(TWELVE_MONTHS, JUNGHOON, StaffRole.OPERATOR, TRANSFERRED_AT, null),
                    new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OWNER, TRANSFERRED_AT, null));
        }

        @Test
        @DisplayName("운영자가 아닌 사람(바깥 사람)에게는 넘길 수 없다 — 먼저 운영자로 초대한다")
        void cannotTransferToOutsider() {
            OrganizerStaff staff = twelveMonthsStaff();

            assertRejected(() -> staff.transferOwnership(JUNGHOON, DOHYUN, TRANSFERRED_AT),
                    OrganizerErrorCode.OWNERSHIP_RECEIVER_NOT_OPERATOR);
            assertThat(staff.currentOwner()).hasValue(JUNGHOON);
            assertThat(staff.roleOf(DOHYUN)).isEmpty();
            assertThat(OrganizerErrorCode.OWNERSHIP_RECEIVER_NOT_OPERATOR.message())
                    .isEqualTo("소유자는 이 주최자의 운영자에게만 넘길 수 있습니다. 바깥 사람이면 먼저 운영자로 초대해 주세요.");
        }

        @Test
        @DisplayName("이미 나간 운영자에게도 넘길 수 없다")
        void cannotTransferToFormerOperator() {
            OrganizerStaff staff = twelveMonthsStaff();
            staff.leave(PLANNER, TRANSFERRED_AT);

            assertRejected(() -> staff.transferOwnership(JUNGHOON, PLANNER, LATER),
                    OrganizerErrorCode.OWNERSHIP_RECEIVER_NOT_OPERATOR);
        }

        @Test
        @DisplayName("자기 자신에게는 넘길 수 없다 — 소유자는 운영자가 아니다")
        void cannotTransferToSelf() {
            OrganizerStaff staff = twelveMonthsStaff();

            assertRejected(() -> staff.transferOwnership(JUNGHOON, JUNGHOON, TRANSFERRED_AT),
                    OrganizerErrorCode.OWNERSHIP_RECEIVER_NOT_OPERATOR);
        }

        @Test
        @DisplayName("운영자는 소유자를 넘길 수 없다 — 소유자만 한다")
        void operatorCannotTransfer() {
            OrganizerStaff staff = twelveMonthsStaff();
            staff.addOperator(DOHYUN, LATER);

            assertPermissionDenied(() -> staff.transferOwnership(PLANNER, DOHYUN, LATER),
                    OrganizerErrorCode.OWNER_ONLY_ACTION);
            assertPermissionDenied(() -> staff.transferOwnership(PLANNER, PLANNER, LATER),
                    OrganizerErrorCode.OWNER_ONLY_ACTION);
            assertThat(staff.currentOwner()).hasValue(JUNGHOON);
        }

        @Test
        @DisplayName("넘겨받은 새 소유자는 다시 넘길 수 있고, 처음 소유자는 운영자라 받을 수 있다")
        void newOwnerCanTransferBack() {
            OrganizerStaff staff = twelveMonthsStaff();
            staff.transferOwnership(JUNGHOON, PLANNER, TRANSFERRED_AT);

            staff.transferOwnership(PLANNER, JUNGHOON, LATER);

            assertThat(staff.currentOwner()).hasValue(JUNGHOON);
            assertThat(staff.roleOf(PLANNER)).contains(StaffRole.OPERATOR);
            assertThat(activeOwnerCount(staff)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("나가기와 내보내기")
    class LeaveAndRemove {

        @Test
        @DisplayName("운영자는 언제든 나간다 — 소속은 끝나고 이력은 남는다")
        void operatorLeavesAnytime() {
            OrganizerStaff staff = twelveMonthsStaff();

            staff.leave(PLANNER, LATER);

            assertThat(staff.roleOf(PLANNER)).isEmpty();
            assertThat(staff.memberships()).contains(
                    new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, PLANNER_JOINED_AT, LATER));
            assertThat(staff.currentOwner()).hasValue(JUNGHOON);
        }

        @Test
        @DisplayName("소유자는 나갈 수 없다 — '먼저 소유자를 넘기거나 주최자를 닫아야 합니다'")
        void ownerCannotLeave() {
            OrganizerStaff staff = twelveMonthsStaff();

            assertRejected(() -> staff.leave(JUNGHOON, LATER), OrganizerErrorCode.OWNER_MUST_HAND_OVER_FIRST);
            assertThat(staff.currentOwner()).hasValue(JUNGHOON);
            assertThat(OrganizerErrorCode.OWNER_MUST_HAND_OVER_FIRST.message())
                    .isEqualTo("먼저 소유자를 넘기거나 주최자를 닫아야 합니다.");
        }

        @Test
        @DisplayName("소유자를 넘기고 운영자가 되면 나갈 수 있다")
        void formerOwnerLeavesAfterTransfer() {
            OrganizerStaff staff = twelveMonthsStaff();
            staff.transferOwnership(JUNGHOON, PLANNER, TRANSFERRED_AT);

            staff.leave(JUNGHOON, LATER);

            assertThat(staff.roleOf(JUNGHOON)).isEmpty();
            assertThat(staff.currentOwner()).hasValue(PLANNER);
        }

        @Test
        @DisplayName("소속되지 않은 사람은 나갈 것이 없다")
        void outsiderCannotLeave() {
            assertPermissionDenied(() -> twelveMonthsStaff().leave(DOHYUN, LATER), OrganizerErrorCode.NOT_ORGANIZER_STAFF);
        }

        @Test
        @DisplayName("소유자가 운영자를 내보낸다 — 내보내진 사람의 이력은 남는다")
        void ownerRemovesOperator() {
            OrganizerStaff staff = twelveMonthsStaff();

            staff.removeOperator(JUNGHOON, PLANNER, LATER);

            assertThat(staff.roleOf(PLANNER)).isEmpty();
            assertThat(staff.memberships()).contains(
                    new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, PLANNER_JOINED_AT, LATER));
        }

        @Test
        @DisplayName("운영자는 다른 운영자를 내보낼 수 없다")
        void operatorCannotRemove() {
            OrganizerStaff staff = twelveMonthsStaff();
            staff.addOperator(DOHYUN, LATER);

            assertPermissionDenied(() -> staff.removeOperator(PLANNER, DOHYUN, LATER), OrganizerErrorCode.OWNER_ONLY_ACTION);
            assertThat(staff.roleOf(DOHYUN)).contains(StaffRole.OPERATOR);
        }

        @Test
        @DisplayName("소유자 자신이나 소속되지 않은 사람은 내보낼 수 없다 — 소유자는 1명 그대로")
        void cannotRemoveOwnerOrOutsider() {
            OrganizerStaff staff = twelveMonthsStaff();

            assertRejected(() -> staff.removeOperator(JUNGHOON, JUNGHOON, LATER), OrganizerErrorCode.NOT_AN_OPERATOR);
            assertRejected(() -> staff.removeOperator(JUNGHOON, DOHYUN, LATER), OrganizerErrorCode.NOT_AN_OPERATOR);
            assertThat(activeOwnerCount(staff)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("저장소에서 되살리기")
    class Restore {

        @Test
        @DisplayName("지난 소속까지 그대로 되살린다")
        void restoresHistory() {
            List<StaffMembership> memberships = List.of(
                    new StaffMembership(TWELVE_MONTHS, JUNGHOON, StaffRole.OWNER, APPROVED_AT, TRANSFERRED_AT),
                    new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, PLANNER_JOINED_AT, TRANSFERRED_AT),
                    new StaffMembership(TWELVE_MONTHS, JUNGHOON, StaffRole.OPERATOR, TRANSFERRED_AT, null),
                    new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OWNER, TRANSFERRED_AT, null));

            OrganizerStaff staff = OrganizerStaff.restore(TWELVE_MONTHS, memberships);

            assertThat(staff.memberships()).isEqualTo(memberships);
            assertThat(staff.currentOwner()).hasValue(PLANNER);
        }

        @Test
        @DisplayName("운영진이 아무도 남지 않은 명단(닫힘)은 소유자 없이 되살린다")
        void restoresDisbandedStaff() {
            OrganizerStaff closed = OrganizerStaff.restore(TWELVE_MONTHS, List.of(
                    new StaffMembership(TWELVE_MONTHS, JUNGHOON, StaffRole.OWNER, APPROVED_AT, LATER),
                    new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, PLANNER_JOINED_AT, LATER)));

            assertThat(closed.currentOwner()).isEmpty();
            assertThat(closed.activeMemberships()).isEmpty();
        }

        @Test
        @DisplayName("소유자가 둘이면 되살리지 않는다 — 소유자는 1명")
        void rejectsTwoOwners() {
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerStaff.restore(TWELVE_MONTHS, List.of(
                    new StaffMembership(TWELVE_MONTHS, JUNGHOON, StaffRole.OWNER, APPROVED_AT, null),
                    new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OWNER, PLANNER_JOINED_AT, null))));
        }

        @Test
        @DisplayName("운영자만 남고 소유자가 없으면 되살리지 않는다")
        void rejectsOperatorsWithoutOwner() {
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerStaff.restore(TWELVE_MONTHS, List.of(
                    new StaffMembership(TWELVE_MONTHS, JUNGHOON, StaffRole.OWNER, APPROVED_AT, TRANSFERRED_AT),
                    new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, PLANNER_JOINED_AT, null))));
        }

        @Test
        @DisplayName("한 사람에게 지금 소속이 둘이거나 다른 주최자의 소속이 섞이면 되살리지 않는다")
        void rejectsInconsistentMemberships() {
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerStaff.restore(TWELVE_MONTHS, List.of(
                    new StaffMembership(TWELVE_MONTHS, JUNGHOON, StaffRole.OWNER, APPROVED_AT, null),
                    new StaffMembership(TWELVE_MONTHS, JUNGHOON, StaffRole.OPERATOR, APPROVED_AT, null))));
            assertThatIllegalArgumentException().isThrownBy(() -> OrganizerStaff.restore(TWELVE_MONTHS, List.of(
                    new StaffMembership(TWELVE_MONTHS, JUNGHOON, StaffRole.OWNER, APPROVED_AT, null),
                    new StaffMembership(99L, PLANNER, StaffRole.OPERATOR, APPROVED_AT, null))));
        }

        @Test
        @DisplayName("소속 종료는 시작보다 앞설 수 없다")
        void membershipCannotEndBeforeStart() {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, LATER, APPROVED_AT));
        }
    }

    @Test
    @DisplayName("역할은 소유자와 운영자 두 가지뿐이다 (docs/99 B \"운영자의 행사별 권한\", \"소유자 여러 명\")")
    void twoRolesOnly() {
        assertThat(Arrays.asList(StaffRole.values())).containsExactly(StaffRole.OWNER, StaffRole.OPERATOR);
    }
}
