package com.ticketon.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("관리자 2등급 권한 표 (docs/01 \"관리자 2등급\")")
class AdminGradeTest {

    /** 최고 관리자는 전부 O, 관리자는 O/X — docs/01 "관리자 2등급" 표를 한 항목씩 옮긴 기대값. */
    private static final Map<AdminAction, Boolean> ADMIN_CAN = Map.ofEntries(
            // '관리자' 줄 — 두 등급 모두
            Map.entry(AdminAction.REVIEW_ORGANIZER_APPLICATION, true),
            Map.entry(AdminAction.APPROVE_ORGANIZER_APPLICATION, true),
            Map.entry(AdminAction.RESET_STAFF_NICKNAME, true),
            Map.entry(AdminAction.RESET_STAFF_INTRODUCTION, true),
            Map.entry(AdminAction.RESET_ORGANIZER_LOGO, true),
            Map.entry(AdminAction.SUSPEND_ORGANIZER, true),
            Map.entry(AdminAction.COMPLETE_SETTLEMENT, true),
            Map.entry(AdminAction.CONFIRM_RECEIVABLE_PAYMENT, true),
            Map.entry(AdminAction.CONFIRM_SETTLEMENT_ACCOUNT_CHANGE, true),
            Map.entry(AdminAction.CONFIRM_BUSINESS_INFORMATION_CHANGE, true),
            Map.entry(AdminAction.PROVIDE_NON_BUSINESS_ORGANIZER_INFORMATION, true),
            Map.entry(AdminAction.TRANSFER_ORGANIZER_OWNERSHIP, true),
            Map.entry(AdminAction.TAKE_DOWN_EVENT_PAGE, true),
            Map.entry(AdminAction.RESTORE_EVENT_PAGE, true),
            Map.entry(AdminAction.REISSUE_TICKET_QR, true),
            Map.entry(AdminAction.SUSPEND_AUDIENCE, true),
            Map.entry(AdminAction.CANCEL_BOOKING_ON_BEHALF, true),
            Map.entry(AdminAction.CONFIRM_FORCE_MAJEURE, true),
            Map.entry(AdminAction.HANDLE_PERFORMANCE_DELAY_ON_BEHALF, true),
            Map.entry(AdminAction.HANDLE_PERFORMER_CHANGE_ON_BEHALF, true),
            Map.entry(AdminAction.SEND_MARKETING_EMAIL, true),
            // '최고 관리자' 줄에 더해진 일 — 최고 관리자만
            Map.entry(AdminAction.PERMANENTLY_SUSPEND_ORGANIZER, false),
            Map.entry(AdminAction.ISSUE_ADMIN_ACCOUNT, false),
            // 표 밖에서 다른 문서가 관리자에게 맡긴 일 — 두 등급 모두
            Map.entry(AdminAction.CONFIRM_OWNERSHIP_CHANGE, true),
            Map.entry(AdminAction.CONFIRM_ORGANIZER_NAME_CHANGE, true));

    static Stream<Arguments> permissionTable() {
        return ADMIN_CAN.entrySet().stream().map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    @Test
    @DisplayName("기대값 표가 모든 일을 빠짐없이 덮는다 — 일을 추가하면 이 표에도 적어야 한다")
    void tableCoversEveryAction() {
        assertThat(ADMIN_CAN.keySet()).containsExactlyInAnyOrder(AdminAction.values());
    }

    @ParameterizedTest(name = "{0} — 최고 관리자 O, 관리자 {1}")
    @MethodSource("permissionTable")
    @DisplayName("표의 항목마다 최고 관리자는 할 수 있고, 관리자는 표의 O/X를 따른다")
    void followsPermissionTable(AdminAction action, boolean adminCan) {
        assertThat(AdminGrade.SUPER_ADMIN.can(action)).isTrue();
        assertThat(AdminGrade.ADMIN.can(action)).isEqualTo(adminCan);
        assertThat(action.isSuperAdminOnly()).isEqualTo(!adminCan);
    }

    @Test
    @DisplayName("최고 관리자만 하는 일은 되돌릴 수 없는 둘 — 영구 정지와 관리자 계정 발급뿐이다")
    void onlyIrreversibleActionsAreSuperAdminOnly() {
        assertThat(Arrays.stream(AdminAction.values()).filter(AdminAction::isSuperAdminOnly))
                .containsExactlyInAnyOrder(AdminAction.PERMANENTLY_SUSPEND_ORGANIZER, AdminAction.ISSUE_ADMIN_ACCOUNT);
    }

    @Test
    @DisplayName("등급은 관리자·최고 관리자 둘뿐이다 — 더 나누지 않는다 (docs/99 B \"관리자 등급 세분화\")")
    void hasTwoGrades() {
        assertThat(AdminGrade.values()).containsExactly(AdminGrade.ADMIN, AdminGrade.SUPER_ADMIN);
        assertThat(AdminGrade.ADMIN.displayName()).isEqualTo("관리자");
        assertThat(AdminGrade.SUPER_ADMIN.displayName()).isEqualTo("최고 관리자");
    }
}
