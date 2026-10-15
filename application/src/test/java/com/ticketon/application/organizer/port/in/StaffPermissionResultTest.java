package com.ticketon.application.organizer.port.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffPermission;
import com.ticketon.domain.organizer.StaffRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 극단 열두달(주최자 12)의 기획 단원(운영자 2) — 확인 결과가 도메인의 판정을 그대로 옮기고, 같은 규칙을 지키는지 본다.
 */
@DisplayName("운영진 권한 확인 결과 (Result)")
class StaffPermissionResultTest {

    private static final long TWELVE_MONTHS = 12L;
    private static final long LATE_SUMMER = 20L;
    private static final long PLANNER = 2L;

    @Test
    @DisplayName("도메인의 확인 결과에서 주최자 · 운영진 · 그때의 역할 · 한 일을 그대로 옮긴다")
    void copiesDomainPermission() {
        StaffPermissionResult result = StaffPermissionResult.from(
                new StaffPermission(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, StaffAction.MANAGE_INVITATIONS));

        assertThat(result).isEqualTo(
                new StaffPermissionResult(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, StaffAction.MANAGE_INVITATIONS));
    }

    @Test
    @DisplayName("확인한 주최자의 자원만 다룬다 — 다른 주최자의 자원은 이 주최자 안에 없는 것으로 본다")
    void coversOnlyCheckedOrganizer() {
        StaffPermissionResult result =
                new StaffPermissionResult(TWELVE_MONTHS, PLANNER, StaffRole.OPERATOR, StaffAction.MANAGE_INVITATIONS);

        assertThat(result.covers(TWELVE_MONTHS)).isTrue();
        assertThat(result.covers(LATE_SUMMER)).isFalse();
    }

    @Test
    @DisplayName("역할로 할 수 없는 일을 담은 결과는 만들 수 없다 — 운영자가 회차를 취소했다는 기록이 생기지 않게")
    void rejectsActionBeyondRole() {
        assertThatIllegalArgumentException().isThrownBy(() -> new StaffPermissionResult(TWELVE_MONTHS, PLANNER,
                StaffRole.OPERATOR, StaffAction.CANCEL_SESSION));
    }
}
