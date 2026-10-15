package com.ticketon.domain.organizer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("운영진 권한 표 (docs/01 \"권한 두 가지\")")
class StaffRoleTest {

    /** 소유자는 O, 운영자는 O/X — docs/01 "권한 두 가지" 표를 한 항목씩 옮긴 기대값. 표 밖 항목은 맨 아래. */
    private static final Map<StaffAction, Boolean> OPERATOR_CAN = Map.ofEntries(
            // 1줄: 행사 만들기·수정, 초대권, 검표 계정, 현황·통계·정산 보기, 주최자 공개 정보(소개·SNS·로고) 편집 — O / O
            Map.entry(StaffAction.CREATE_EVENT, true),
            Map.entry(StaffAction.EDIT_EVENT, true),
            Map.entry(StaffAction.MANAGE_INVITATIONS, true),
            Map.entry(StaffAction.MANAGE_CHECKIN_ACCOUNTS, true),
            Map.entry(StaffAction.VIEW_STATUS, true),
            Map.entry(StaffAction.VIEW_STATISTICS, true),
            Map.entry(StaffAction.VIEW_SETTLEMENT, true),
            Map.entry(StaffAction.EDIT_PUBLIC_PROFILE, true),
            // 2줄: 판매 중단·재개, 연기·장소 변경 신청 — O / O
            Map.entry(StaffAction.PAUSE_SALES, true),
            Map.entry(StaffAction.RESUME_SALES, true),
            Map.entry(StaffAction.REQUEST_POSTPONEMENT, true),
            Map.entry(StaffAction.REQUEST_VENUE_CHANGE, true),
            // 3줄: 행사 취소 · 회차 취소 · 공연 중단 처리 — O / X
            Map.entry(StaffAction.CANCEL_EVENT, false),
            Map.entry(StaffAction.CANCEL_SESSION, false),
            Map.entry(StaffAction.HANDLE_PERFORMANCE_INTERRUPTION, false),
            // 4줄: 출연자 변경 알림 · 공연 지연 처리 — O / X
            Map.entry(StaffAction.NOTIFY_PERFORMER_CHANGE, false),
            Map.entry(StaffAction.HANDLE_PERFORMANCE_DELAY, false),
            // 5줄: 판매를 시작한 행사의 삭제 — O / X
            Map.entry(StaffAction.DELETE_EVENT_AFTER_SALES_STARTED, false),
            // 6줄: 정산 계좌 — O / X
            Map.entry(StaffAction.MANAGE_SETTLEMENT_ACCOUNT, false),
            // 7줄: 운영진 초대·내보내기 — O / X
            Map.entry(StaffAction.INVITE_STAFF, false),
            Map.entry(StaffAction.REMOVE_STAFF, false),
            // 8줄: 소유자 넘기기 · 주최자 닫기 — O / X
            Map.entry(StaffAction.TRANSFER_OWNERSHIP, false),
            Map.entry(StaffAction.CLOSE_ORGANIZER, false),
            // 표 밖: 판매 시작 전 행사 삭제(docs/11), 주최자명 변경(docs/01·11), 사업자 정보 변경(docs/01·11),
            // 운영진 목록 보기·이 주최자에서 나가기·소유자 이전 신청(docs/13 "운영진 관리"), 운영자 초대 수락(docs/01 "운영자 초대")
            Map.entry(StaffAction.DELETE_EVENT_BEFORE_SALES_STARTED, true),
            Map.entry(StaffAction.RENAME_ORGANIZER, false),
            Map.entry(StaffAction.CHANGE_BUSINESS_INFORMATION, false),
            Map.entry(StaffAction.VIEW_STAFF, true),
            Map.entry(StaffAction.ACCEPT_STAFF_INVITATION, true),
            Map.entry(StaffAction.LEAVE_ORGANIZER, true),
            Map.entry(StaffAction.CLAIM_OWNERSHIP, true));

    static Stream<Arguments> permissionTable() {
        return OPERATOR_CAN.entrySet().stream().map(entry -> Arguments.of(entry.getKey(), entry.getValue()));
    }

    @Test
    @DisplayName("기대값 표가 모든 일을 빠짐없이 덮는다 — 일을 추가하면 이 표에도 적어야 한다")
    void tableCoversEveryAction() {
        assertThat(OPERATOR_CAN.keySet()).containsExactlyInAnyOrder(StaffAction.values());
    }

    @ParameterizedTest(name = "{0} — 소유자 O, 운영자 {1}")
    @MethodSource("permissionTable")
    @DisplayName("표의 항목마다 소유자는 할 수 있고, 운영자는 표의 O/X를 따른다")
    void followsPermissionTable(StaffAction action, boolean operatorCan) {
        assertThat(StaffRole.OWNER.can(action)).isTrue();
        assertThat(StaffRole.OPERATOR.can(action)).isEqualTo(operatorCan);
        assertThat(action.isOwnerOnly()).isEqualTo(!operatorCan);
    }

    @Nested
    @DisplayName("표의 줄 묶음")
    class Rows {

        @Test
        @DisplayName("운영자는 배상이 걸린 일(행사 취소·회차 취소·공연 중단)과 배상·환급이 걸린 일(출연자 변경·공연 지연)을 못 한다")
        void operatorCannotHandleCompensation() {
            assertThat(Stream.of(StaffAction.CANCEL_EVENT, StaffAction.CANCEL_SESSION,
                    StaffAction.HANDLE_PERFORMANCE_INTERRUPTION, StaffAction.NOTIFY_PERFORMER_CHANGE,
                    StaffAction.HANDLE_PERFORMANCE_DELAY))
                    .noneMatch(StaffRole.OPERATOR::can);
        }

        @Test
        @DisplayName("운영자는 정산 계좌, 운영진 초대·내보내기, 소유자 넘기기·주최자 닫기를 못 한다")
        void operatorCannotManageOrganizer() {
            assertThat(Stream.of(StaffAction.MANAGE_SETTLEMENT_ACCOUNT, StaffAction.INVITE_STAFF,
                    StaffAction.REMOVE_STAFF, StaffAction.TRANSFER_OWNERSHIP, StaffAction.CLOSE_ORGANIZER))
                    .noneMatch(StaffRole.OPERATOR::can);
        }

        @Test
        @DisplayName("행사 삭제는 판매 시작 전이면 운영자도, 판매를 시작한 뒤면 소유자만 한다 (docs/01 5줄, docs/11)")
        void deletingEventDependsOnSalesStarted() {
            assertThat(StaffRole.OPERATOR.can(StaffAction.DELETE_EVENT_BEFORE_SALES_STARTED)).isTrue();
            assertThat(StaffRole.OPERATOR.can(StaffAction.DELETE_EVENT_AFTER_SALES_STARTED)).isFalse();
            assertThat(StaffRole.OWNER.can(StaffAction.DELETE_EVENT_AFTER_SALES_STARTED)).isTrue();
        }

        @Test
        @DisplayName("소유자는 모든 일을 한다")
        void ownerCanDoEverything() {
            assertThat(Arrays.stream(StaffAction.values())).allMatch(StaffRole.OWNER::can);
        }
    }
}
