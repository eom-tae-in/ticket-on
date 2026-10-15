package com.ticketon.domain.organizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

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
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 극단 열두달(docs/15 박정훈) — 대표 박정훈이 소유자, 기획 단원이 운영자, 지난해 나간 단원이 있다. 이도현은 다른 주최자 늦여름의 운영자라
 * 열두달에는 바깥 사람이다(한 사람이 여러 주최자에 소속돼도 권한은 주최자마다 따로다).
 */
@DisplayName("운영진 권한 확인 — 소속 · 역할 · 주최자 상태 (docs/01 \"권한 두 가지\")")
class StaffPermissionTest {

    private static final long TWELVE_MONTHS = 12L;
    private static final long LATE_SUMMER = 20L;
    private static final long JUNGHOON = 1L;
    private static final long PLANNER = 2L;
    private static final long FORMER_MEMBER = 3L;
    private static final long DOHYUN = 4L;

    private static final Instant APPROVED_AT = at(2019, 3, 4);
    private static final Instant JOINED_AT = at(2019, 5, 1);
    private static final Instant LEFT_AT = at(2025, 2, 28);

    private static Instant at(int year, int month, int day) {
        return KoreanTime.toInstant(LocalDateTime.of(year, month, day, 10, 0));
    }

    private static Organizer twelveMonths(OrganizerStatus status) {
        return Organizer.restore(TWELVE_MONTHS, OrganizerName.of("극단 열두달"), status, OrganizerPublicProfile.empty());
    }

    /** 소유자 박정훈, 운영자 기획 단원, 지난해 나간 단원. */
    private static OrganizerStaff twelveMonthsStaff() {
        return OrganizerStaff.restore(TWELVE_MONTHS, List.of(
                new StaffMembership(TWELVE_MONTHS, JUNGHOON, StaffRole.OWNER, APPROVED_AT, null),
                new StaffMembership(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, JOINED_AT, null),
                new StaffMembership(TWELVE_MONTHS, FORMER_MEMBER, StaffRole.OPERATOR, JOINED_AT, LEFT_AT)));
    }

    private static StaffPermission check(OrganizerStatus status, long staffAccountId, StaffAction action) {
        return StaffPermission.check(twelveMonths(status), twelveMonthsStaff(), staffAccountId, action);
    }

    private static void assertPermissionDenied(ThrowingCallable action, OrganizerErrorCode expectedCode) {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    static List<StaffAction> ownerOnlyActions() {
        return Arrays.stream(StaffAction.values()).filter(StaffAction::isOwnerOnly).toList();
    }

    static List<StaffAction> sharedActions() {
        return Arrays.stream(StaffAction.values()).filter(action -> !action.isOwnerOnly()).toList();
    }

    @Nested
    @DisplayName("활동 중인 주최자")
    class ActiveOrganizer {

        @ParameterizedTest(name = "{0}")
        @EnumSource(StaffAction.class)
        @DisplayName("소유자는 모든 일을 할 수 있고, 확인 결과에 주최자 · 사람 · 소유자 역할 · 한 일이 남는다")
        void ownerIsPermittedEverything(StaffAction action) {
            assertThat(check(OrganizerStatus.ACTIVE, JUNGHOON, action))
                    .isEqualTo(new StaffPermission(TWELVE_MONTHS, JUNGHOON, StaffRole.OWNER, action));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ticketon.domain.organizer.StaffPermissionTest#sharedActions")
        @DisplayName("운영자는 운영진 공통의 일을 할 수 있다 — 행사 만들기·수정, 초대권, 검표 계정, 현황·통계·정산 보기, 판매 중단·재개 등")
        void operatorIsPermittedSharedActions(StaffAction action) {
            assertThat(check(OrganizerStatus.ACTIVE, PLANNER, action))
                    .isEqualTo(new StaffPermission(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, action));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.ticketon.domain.organizer.StaffPermissionTest#ownerOnlyActions")
        @DisplayName("운영자는 소유자만 하는 일(배상 · 정산 계좌 · 운영진 관리 등)에서 '소유자만 할 수 있는 일입니다'로 막힌다")
        void operatorIsDeniedOwnerOnlyActions(StaffAction action) {
            assertPermissionDenied(() -> check(OrganizerStatus.ACTIVE, PLANNER, action),
                    OrganizerErrorCode.OWNER_ONLY_ACTION);
        }

        @Test
        @DisplayName("늦여름의 운영자는 늦여름에서는 되지만 열두달에서는 공통의 일도 '이 주최자의 운영진이 아닙니다'로 막힌다 — 주최자 id만 바꿔 "
                + "들어오지 못한다")
        void otherOrganizerStaffIsDenied() {
            Organizer lateSummer = Organizer.restore(LATE_SUMMER, OrganizerName.of("늦여름"), OrganizerStatus.ACTIVE,
                    OrganizerPublicProfile.empty());
            OrganizerStaff lateSummerStaff = OrganizerStaff.startWithOwner(LATE_SUMMER, 5L, APPROVED_AT);
            lateSummerStaff.addOperator(DOHYUN, JOINED_AT);

            assertThat(StaffPermission.check(lateSummer, lateSummerStaff, DOHYUN, StaffAction.VIEW_STATUS).role())
                    .isEqualTo(StaffRole.OPERATOR);
            assertPermissionDenied(() -> check(OrganizerStatus.ACTIVE, DOHYUN, StaffAction.VIEW_STATUS),
                    OrganizerErrorCode.NOT_ORGANIZER_STAFF);
        }

        @Test
        @DisplayName("이미 나간 사람은 막힌다 — 나온 주최자의 정산·현황은 볼 수 없다(docs/01 \"지난 소속\")")
        void formerMemberIsDenied() {
            assertPermissionDenied(() -> check(OrganizerStatus.ACTIVE, FORMER_MEMBER, StaffAction.VIEW_SETTLEMENT),
                    OrganizerErrorCode.NOT_ORGANIZER_STAFF);
        }
    }

    @Nested
    @DisplayName("활동 중이 아닌 주최자")
    class InactiveOrganizer {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = OrganizerStatus.class, names = {"UNDER_REVIEW", "CLOSED", "PERMANENTLY_SUSPENDED"})
        @DisplayName("소속 줄이 남아 있어도 소유자의 어떤 일이든 '활동 중인 주최자가 아닙니다'로 막는다")
        void ownerIsDeniedWhenNotActive(OrganizerStatus status) {
            assertPermissionDenied(() -> check(status, JUNGHOON, StaffAction.VIEW_STATUS),
                    OrganizerErrorCode.ORGANIZER_NOT_ACTIVE);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = OrganizerStatus.class, names = {"UNDER_REVIEW", "CLOSED", "PERMANENTLY_SUSPENDED"})
        @DisplayName("소속되지 않은 사람에게는 주최자 상태를 알려 주지 않는다 — 소속을 먼저 본다")
        void outsiderLearnsNothingAboutStatus(OrganizerStatus status) {
            assertPermissionDenied(() -> check(status, DOHYUN, StaffAction.VIEW_STATUS),
                    OrganizerErrorCode.NOT_ORGANIZER_STAFF);
        }

        @Test
        @DisplayName("심사 중인 주최자에는 소속이 없어 신청한 사람도 운영진이 아니다 — 소속은 승인 때 시작한다")
        void applicantIsNotStaffWhileUnderReview() {
            Organizer underReview = twelveMonths(OrganizerStatus.UNDER_REVIEW);
            OrganizerStaff noStaff = OrganizerStaff.restore(TWELVE_MONTHS, List.of());

            assertPermissionDenied(() -> StaffPermission.check(underReview, noStaff, JUNGHOON, StaffAction.CREATE_EVENT),
                    OrganizerErrorCode.NOT_ORGANIZER_STAFF);
        }
    }

    @Nested
    @DisplayName("자원이 이 주최자의 것인지")
    class Covers {

        @Test
        @DisplayName("같은 주최자의 자원만 다룬다 — 다른 주최자의 자원은 이 주최자 안에 없는 것으로 본다")
        void coversOnlyOwnOrganizer() {
            StaffPermission permission = check(OrganizerStatus.ACTIVE, PLANNER, StaffAction.MANAGE_INVITATIONS);

            assertThat(permission.covers(TWELVE_MONTHS)).isTrue();
            assertThat(permission.covers(LATE_SUMMER)).isFalse();
        }
    }

    @Nested
    @DisplayName("잘못 부르면")
    class Misuse {

        @Test
        @DisplayName("다른 주최자의 명단으로 확인하지 않는다")
        void rejectsOtherOrganizerStaffList() {
            OrganizerStaff lateSummerStaff = OrganizerStaff.startWithOwner(LATE_SUMMER, JUNGHOON, APPROVED_AT);

            assertThatIllegalArgumentException().isThrownBy(() -> StaffPermission.check(
                    twelveMonths(OrganizerStatus.ACTIVE), lateSummerStaff, JUNGHOON, StaffAction.CREATE_EVENT));
        }

        @Test
        @DisplayName("저장 전 주최자의 권한은 확인하지 않는다")
        void rejectsUnsavedOrganizer() {
            assertThatIllegalArgumentException().isThrownBy(() -> StaffPermission.check(
                    Organizer.underReview(OrganizerName.of("극단 열두달")), twelveMonthsStaff(), JUNGHOON,
                    StaffAction.CREATE_EVENT));
        }

        @Test
        @DisplayName("역할로 할 수 없는 일을 담은 확인 결과는 만들 수 없다 — 운영자가 회차를 취소했다는 기록이 생기지 않게")
        void rejectsPermissionBeyondRole() {
            assertThatIllegalArgumentException().isThrownBy(() -> new StaffPermission(TWELVE_MONTHS, PLANNER,
                    StaffRole.OPERATOR, StaffAction.CANCEL_SESSION));
        }
    }
}
