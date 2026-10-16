package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventEditAvailability.ALLOWED;
import static com.ticketon.domain.event.EventEditAvailability.ALLOWED_WITH_RULES;
import static com.ticketon.domain.event.EventEditAvailability.LOCKED;
import static com.ticketon.domain.event.EventEditAvailability.NEEDS_ADMIN_APPROVAL;
import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static com.ticketon.domain.event.EventStatusFixtures.MARCH_14;
import static com.ticketon.domain.event.EventStatusFixtures.MARCH_15;
import static com.ticketon.domain.event.EventStatusFixtures.eightPerformances;
import static com.ticketon.domain.event.EventStatusFixtures.korean;
import static com.ticketon.domain.event.EventStatusFixtures.singlePerformance;
import static com.ticketon.domain.event.EventStatusFixtures.springPlayIn;
import static com.ticketon.domain.event.EventStatusFixtures.withOpensAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.ticketon.domain.error.NotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("상태별 수정 범위 (docs/04 \"상태별로 할 수 있는 것\", \"예매가 열린 뒤(판매 중) 수정 범위\", \"판매 종료는 공연이 끝난 때가 아닙니다\")")
class EventEditScopeTest {

    /** docs/04 "예매가 열린 뒤(판매 중) 수정 범위" 표 그대로 — 표 밖 항목은 그 근거 또는 확인 필요. */
    private static final Map<EventEditTarget, EventEditAvailability> AFTER_BOOKING_OPENED = afterBookingOpenedTable();

    private static Map<EventEditTarget, EventEditAvailability> afterBookingOpenedTable() {
        Map<EventEditTarget, EventEditAvailability> table = new EnumMap<>(EventEditTarget.class);
        table.put(EventEditTarget.VENUE, NEEDS_ADMIN_APPROVAL);
        table.put(EventEditTarget.SESSION_DATE_TIME, ALLOWED_WITH_RULES);
        table.put(EventEditTarget.TITLE_POSTER_DESCRIPTION, ALLOWED);
        table.put(EventEditTarget.ENTRANCES, ALLOWED);
        table.put(EventEditTarget.CHECKIN_ACCOUNTS, ALLOWED);
        table.put(EventEditTarget.VISIBILITY, ALLOWED);
        table.put(EventEditTarget.BOOKING_CLOSE_TIME, ALLOWED);
        table.put(EventEditTarget.CAPACITY, ALLOWED_WITH_RULES);
        table.put(EventEditTarget.FLOOR_PLAN, ALLOWED_WITH_RULES);
        table.put(EventEditTarget.BLOCK_NAME, ALLOWED);
        table.put(EventEditTarget.EXCLUDED_SEATS, ALLOWED_WITH_RULES);
        table.put(EventEditTarget.PRICE, ALLOWED);
        table.put(EventEditTarget.SESSION_ADDITION, ALLOWED);
        table.put(EventEditTarget.SESSION_OR_ZONE_DELETION, ALLOWED_WITH_RULES);
        table.put(EventEditTarget.CANCELLATION_POLICY, LOCKED);
        table.put(EventEditTarget.PERFORMERS, ALLOWED);
        table.put(EventEditTarget.MISSING_SESSION_END_TIME, ALLOWED);
        table.put(EventEditTarget.TYPE_AND_CATEGORY, LOCKED);
        table.put(EventEditTarget.VENUE_GUIDE_AND_CONTACT, LOCKED);
        table.put(EventEditTarget.OTHER_BOOKING_SETTINGS, LOCKED);
        return table;
    }

    /** 판매 대기에 초대권이 걸리면 오픈 전부터 규칙이 붙는 항목 — 좌석 이동 · 삭제와 판매 제외 해제, 회차 · 구역 삭제, 회차 시간 알림, 장소 승인. */
    private static final Set<EventEditTarget> LOCKED_BY_INVITATIONS = Set.of(EventEditTarget.VENUE, EventEditTarget.SESSION_DATE_TIME,
            EventEditTarget.FLOOR_PLAN, EventEditTarget.EXCLUDED_SEATS, EventEditTarget.SESSION_OR_ZONE_DELETION);

    /** 판매가 끝난 뒤에도 고치는 항목 — 입구 목록 · 검표 계정(+ 확인 필요: 비어 있던 회차 종료 시각). */
    private static final Set<EventEditTarget> ON_SITE = Set.of(EventEditTarget.ENTRANCES, EventEditTarget.CHECKIN_ACCOUNTS,
            EventEditTarget.MISSING_SESSION_END_TIME);

    @Nested
    @DisplayName("표 — 상태마다 항목마다")
    class Table {

        @ParameterizedTest(name = "{0}")
        @EnumSource(EventEditTarget.class)
        @DisplayName("작성 중 — 전부")
        void draftEditsEverything(EventEditTarget target) {
            assertThat(springPlayIn(EventStatus.DRAFT).editAvailability(target)).isEqualTo(ALLOWED);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(EventEditTarget.class)
        @DisplayName("판매 대기 — 전부. 초대권이 걸린 것에는 오픈 전부터 규칙이 붙는다 (취소 규정 · 정원 · 가격은 계속 자유)")
        void awaitingEditsEverythingExceptInvited(EventEditTarget target) {
            EventEditAvailability expected = LOCKED_BY_INVITATIONS.contains(target) ? ALLOWED_WITH_RULES : ALLOWED;

            assertThat(springPlayIn(EventStatus.AWAITING_OPEN).editAvailability(target)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(EventEditTarget.class)
        @DisplayName("판매 중 — 예매가 열린 뒤 수정 범위 표 그대로")
        void onSaleFollowsTable(EventEditTarget target) {
            assertThat(springPlayIn(EventStatus.ON_SALE).editAvailability(target)).isEqualTo(AFTER_BOOKING_OPENED.get(target));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(EventEditTarget.class)
        @DisplayName("판매 중단 — 판매 중의 변형이라 판매 중과 같다")
        void pausedFollowsTable(EventEditTarget target) {
            assertThat(springPlayIn(EventStatus.SALES_PAUSED).editAvailability(target)).isEqualTo(AFTER_BOOKING_OPENED.get(target));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(EventEditTarget.class)
        @DisplayName("판매 종료 ~ 행사 종료 — 입구 목록 · 검표 계정만")
        void salesClosedEditsOnSiteOnly(EventEditTarget target) {
            EventEditAvailability expected = ON_SITE.contains(target) ? ALLOWED : LOCKED;

            assertThat(springPlayIn(EventStatus.SALES_CLOSED).editAvailability(target)).isEqualTo(expected);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EventStatus.class, names = {"ENDED", "SETTLED", "CANCELLED"})
        @DisplayName("행사 종료 이후 (정산 완료 · 취소됨 포함) — 없음, 전부 고정")
        void finishedEditsNothing(EventStatus status) {
            Event event = springPlayIn(status);

            for (EventEditTarget target : EventEditTarget.values()) {
                assertThat(event.editAvailability(target)).as(target.name()).isEqualTo(LOCKED);
            }
        }
    }

    @Nested
    @DisplayName("확인 — 막히면 상태에 맞는 오류")
    class RequireEditable {

        @Test
        @DisplayName("판매 종료 상태에서 제목은 고칠 수 없고 입구는 고칠 수 있다")
        void salesClosedLocksTitleButNotEntrances() {
            Event event = springPlayIn(EventStatus.SALES_CLOSED);

            assertRejected(() -> event.requireEditable(EventEditTarget.TITLE_POSTER_DESCRIPTION),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertThat(event.requireEditable(EventEditTarget.ENTRANCES)).isEqualTo(ALLOWED);
        }

        @Test
        @DisplayName("18:10 옆문 추가 · 18:20 검표 계정 비밀번호 변경 — 18:00 판매 종료를 반영해도 되고, 도면 · 공개 수준은 고정이다 (docs/04 예시)")
        void onSiteChangesAfterSixPm() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            event.advanceByTime(singlePerformance(), korean(MARCH_14, 18, 10));

            assertThat(event.requireEditable(EventEditTarget.ENTRANCES)).isEqualTo(ALLOWED);
            assertThat(event.requireEditable(EventEditTarget.CHECKIN_ACCOUNTS)).isEqualTo(ALLOWED);
            assertRejected(() -> event.requireEditable(EventEditTarget.FLOOR_PLAN), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertRejected(() -> event.requireEditable(EventEditTarget.VISIBILITY), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
        }

        @Test
        @DisplayName("행사 종료 뒤에는 입구 목록도 고칠 수 없다")
        void endedLocksEntrances() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            event.advanceByTime(singlePerformance(), korean(MARCH_14, 21, 0));

            assertRejected(() -> event.requireEditable(EventEditTarget.ENTRANCES), EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT);
        }

        @Test
        @DisplayName("취소된 행사도 지난 행사라 고칠 수 없다")
        void cancelledIsPastEvent() {
            assertRejected(() -> springPlayIn(EventStatus.CANCELLED).requireEditable(EventEditTarget.TITLE_POSTER_DESCRIPTION),
                    EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT);
        }

        @Test
        @DisplayName("예매가 열린 뒤에는 취소 규정을 바꿀 수 없다 — 판매 대기에는 바꿀 수 있다")
        void cancellationPolicyLockedAfterOpening() {
            assertRejected(() -> springPlayIn(EventStatus.ON_SALE).requireEditable(EventEditTarget.CANCELLATION_POLICY),
                    EventErrorCode.EDIT_LOCKED_AFTER_BOOKING_OPENED);
            assertThat(springPlayIn(EventStatus.AWAITING_OPEN).requireEditable(EventEditTarget.CANCELLATION_POLICY)).isEqualTo(ALLOWED);
        }

        @Test
        @DisplayName("예매가 열린 뒤 장소는 혼자 바꿀 수 없고 관리자 승인을 거친다")
        void venueNeedsApprovalAfterOpening() {
            assertRejected(() -> springPlayIn(EventStatus.ON_SALE).requireEditable(EventEditTarget.VENUE),
                    EventErrorCode.VENUE_CHANGE_NEEDS_ADMIN_APPROVAL);
        }

        @Test
        @DisplayName("항목 규칙이 붙는 항목은 막지 않고 규칙이 붙는다고 알려 준다 — 판매 중 도면")
        void ruledTargetIsNotRejected() {
            assertThat(springPlayIn(EventStatus.ON_SALE).requireEditable(EventEditTarget.FLOOR_PLAN)).isEqualTo(ALLOWED_WITH_RULES);
        }
    }

    @Nested
    @DisplayName("회차 시간 — 예매가 열린 뒤 그 회차 시작 1주일 전까지, 회차마다 따로 (docs/04 \"회차 시간 잠금은 회차마다 따로입니다\")")
    class SessionTime {

        private static final long LAST_SESSION = 108L;
        private static final long MARCH_15_EVENING = 104L;

        @Test
        @DisplayName("3/22 19:00 회차는 1주일 전 3/15 19:00부터 잠긴다 — 그 직전까지 바꿀 수 있다")
        void lockedFromOneWeekBefore() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = eightPerformances(Set.of());

            assertThatNoException().isThrownBy(
                    () -> event.requireSessionTimeEditable(schedule, LAST_SESSION, korean(MARCH_15, 19, 0).minusSeconds(1)));
            assertRejected(() -> event.requireSessionTimeEditable(schedule, LAST_SESSION, korean(MARCH_15, 19, 0)),
                    EventErrorCode.SESSION_TIME_EDIT_LOCKED);
        }

        @Test
        @DisplayName("시작 8일 전에는 바꾸고 6일 전에는 막는다")
        void eightDaysAllowedSixDaysLocked() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = eightPerformances(Set.of());

            assertThatNoException().isThrownBy(() -> event.requireSessionTimeEditable(schedule, LAST_SESSION, korean(MARCH_14, 19, 0)));
            assertRejected(() -> event.requireSessionTimeEditable(schedule, LAST_SESSION, korean(LocalDate.of(2026, 3, 16), 19, 0)),
                    EventErrorCode.SESSION_TIME_EDIT_LOCKED);
        }

        @Test
        @DisplayName("첫 공연이 시작했어도 마지막 주 회차는 바꿀 수 있고, 같은 때 3/15 저녁 회차는 잠겨 있다")
        void eachSessionLockedSeparately() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = eightPerformances(Set.of());
            Instant firstPerformanceStarted = korean(MARCH_14, 15, 30);

            assertThatNoException().isThrownBy(() -> event.requireSessionTimeEditable(schedule, LAST_SESSION, firstPerformanceStarted));
            assertRejected(() -> event.requireSessionTimeEditable(schedule, MARCH_15_EVENING, firstPerformanceStarted),
                    EventErrorCode.SESSION_TIME_EDIT_LOCKED);
        }

        @Test
        @DisplayName("판매 대기는 예매가 없어 1주일 전이 지나도 잠그지 않는다")
        void notLockedBeforeOpening() {
            Event event = springPlayIn(EventStatus.AWAITING_OPEN, withOpensAt(korean(LocalDate.of(2026, 3, 20), 12, 0)));

            assertThatNoException().isThrownBy(() -> event.requireSessionTimeEditable(eightPerformances(Set.of()), LAST_SESSION,
                    korean(LocalDate.of(2026, 3, 18), 12, 0)));
        }

        @Test
        @DisplayName("판매가 끝난 뒤에는 회차 시간을 바꿀 수 없고, 비어 있던 종료 시각 입력은 받는다 (확인 필요)")
        void salesClosedLocksSessionTime() {
            Event event = springPlayIn(EventStatus.SALES_CLOSED);

            assertRejected(() -> event.requireSessionTimeEditable(singlePerformance(), 101L, korean(MARCH_14, 18, 30)),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertThat(event.requireEditable(EventEditTarget.MISSING_SESSION_END_TIME)).isEqualTo(ALLOWED);
        }

        @Test
        @DisplayName("일정에 없는 회차는 찾을 수 없다")
        void unknownSession() {
            Event event = springPlayIn(EventStatus.ON_SALE);

            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> event.requireSessionTimeEditable(singlePerformance(), 999L, korean(MARCH_14, 12, 0)));
        }
    }
}
