package com.ticketon.application.organizer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.CheckStaffPermissionQuery;
import com.ticketon.application.organizer.port.in.StaffPermissionResult;
import com.ticketon.common.KoreanTime;
import com.ticketon.domain.account.AccountKind;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.error.PermissionDeniedException;
import com.ticketon.domain.organizer.OrganizerErrorCode;
import com.ticketon.domain.organizer.OrganizerStaff;
import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.time.LocalDateTime;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달(대표 박정훈 소유자, 기획 단원 운영자)과 늦여름(이도현 소유자) — 둘 다 활동 중. 한 사람이 주최자 id만 바꿔 다른 주최자의 일을
 * 하려는 경우를 포함한다.
 */
@DisplayName("운영진 권한 확인 유스케이스 — 소속 · 역할 · 주최자 상태")
class CheckStaffPermissionServiceTest {

    private static final long JUNGHOON = 1L;
    private static final long PLANNER = 2L;
    private static final long DOHYUN = 4L;

    private static final Instant APPROVED_AT = KoreanTime.toInstant(LocalDateTime.of(2019, 3, 4, 10, 0));

    private final FakeOrganizerRepository organizerRepository = new FakeOrganizerRepository();
    private final FakeOrganizerStaffRepository staffRepository = new FakeOrganizerStaffRepository();
    private final CheckStaffPermissionService service =
            new CheckStaffPermissionService(organizerRepository, staffRepository);

    private long twelveMonths;
    private long lateSummer;

    @BeforeEach
    void storeOrganizers() {
        twelveMonths = organizerRepository.store("극단 열두달", OrganizerStatus.ACTIVE).id().orElseThrow();
        OrganizerStaff twelveMonthsStaff = OrganizerStaff.startWithOwner(twelveMonths, JUNGHOON, APPROVED_AT);
        twelveMonthsStaff.addOperator(PLANNER, APPROVED_AT.plusSeconds(86_400));
        staffRepository.store(twelveMonthsStaff);

        lateSummer = organizerRepository.store("늦여름", OrganizerStatus.ACTIVE).id().orElseThrow();
        staffRepository.store(OrganizerStaff.startWithOwner(lateSummer, DOHYUN, APPROVED_AT));
    }

    private StaffPermissionResult check(long staffAccountId, long organizerId, StaffAction action) {
        return service.check(new CheckStaffPermissionQuery(
                new ActingStaff(AccountRef.of(AccountKind.STAFF, staffAccountId), organizerId), action));
    }

    private static void assertPermissionDenied(ThrowingCallable action, OrganizerErrorCode expectedCode) {
        assertThatExceptionOfType(PermissionDeniedException.class)
                .isThrownBy(action)
                .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(expectedCode));
    }

    @Test
    @DisplayName("소유자는 소유자만 하는 일(회차 취소)을 할 수 있고, 확인 결과에 소유자 역할이 담긴다")
    void ownerIsPermittedOwnerOnlyAction() {
        assertThat(check(JUNGHOON, twelveMonths, StaffAction.CANCEL_SESSION))
                .isEqualTo(new StaffPermissionResult(twelveMonths, JUNGHOON, StaffRole.OWNER,
                        StaffAction.CANCEL_SESSION));
    }

    @Test
    @DisplayName("운영자는 운영진 공통의 일(초대권)을 할 수 있다")
    void operatorIsPermittedSharedAction() {
        assertThat(check(PLANNER, twelveMonths, StaffAction.MANAGE_INVITATIONS).role()).isEqualTo(StaffRole.OPERATOR);
    }

    @Test
    @DisplayName("확인 결과는 확인한 주최자의 자원만 다룬다 — 다른 주최자의 자원은 이 주최자 안에 없는 것으로 본다")
    void resultCoversOnlyCheckedOrganizer() {
        StaffPermissionResult permission = check(PLANNER, twelveMonths, StaffAction.MANAGE_INVITATIONS);

        assertThat(permission.covers(twelveMonths)).isTrue();
        assertThat(permission.covers(lateSummer)).isFalse();
    }

    @Test
    @DisplayName("운영자가 소유자만 하는 일(회차 취소)을 하려 하면 권한 없음 — '소유자만 할 수 있는 일입니다'")
    void operatorIsDeniedOwnerOnlyAction() {
        assertPermissionDenied(() -> check(PLANNER, twelveMonths, StaffAction.CANCEL_SESSION),
                OrganizerErrorCode.OWNER_ONLY_ACTION);
    }

    @Test
    @DisplayName("다른 주최자의 소유자가 주최자 id만 바꿔 오면 공통의 일도 권한 없음 — '이 주최자의 운영진이 아닙니다'")
    void otherOrganizerOwnerIsDenied() {
        assertPermissionDenied(() -> check(DOHYUN, twelveMonths, StaffAction.VIEW_STATUS),
                OrganizerErrorCode.NOT_ORGANIZER_STAFF);
        assertPermissionDenied(() -> check(PLANNER, lateSummer, StaffAction.VIEW_STATUS),
                OrganizerErrorCode.NOT_ORGANIZER_STAFF);
    }

    @Test
    @DisplayName("없는 주최자도 '이 주최자의 운영진이 아닙니다'로 같게 답한다 — id를 넣어 보며 주최자가 있는지 알아낼 수 없게")
    void missingOrganizerAnswersSameAsNotStaff() {
        assertPermissionDenied(() -> check(JUNGHOON, 99L, StaffAction.VIEW_STATUS), OrganizerErrorCode.NOT_ORGANIZER_STAFF);
    }

    @Test
    @DisplayName("닫힌 주최자에 소속 줄이 남아 있어도 권한 없음 — '활동 중인 주최자가 아닙니다'")
    void closedOrganizerIsDenied() {
        long closed = organizerRepository.store("극단 늦봄", OrganizerStatus.CLOSED).id().orElseThrow();
        staffRepository.store(OrganizerStaff.startWithOwner(closed, JUNGHOON, APPROVED_AT));

        assertPermissionDenied(() -> check(JUNGHOON, closed, StaffAction.VIEW_SETTLEMENT),
                OrganizerErrorCode.ORGANIZER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("심사 중인 주최자에는 소속이 없어 신청한 사람도 운영진이 아니다")
    void applicantOfOrganizerUnderReviewIsNotStaff() {
        long underReview = organizerRepository.store("극단 새봄", OrganizerStatus.UNDER_REVIEW).id().orElseThrow();

        assertPermissionDenied(() -> check(PLANNER, underReview, StaffAction.CREATE_EVENT),
                OrganizerErrorCode.NOT_ORGANIZER_STAFF);
    }
}
