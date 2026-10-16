package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.TWELVE_MONTHS_ORGANIZER;
import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static com.ticketon.domain.event.EventFixtures.twelveMonthsSpringPlay;
import static com.ticketon.domain.event.EventStatusFixtures.ADDRESS;
import static com.ticketon.domain.event.EventStatusFixtures.AFTER_EVENT_ENDED;
import static com.ticketon.domain.event.EventStatusFixtures.BEFORE_OPEN;
import static com.ticketon.domain.event.EventStatusFixtures.DURING_SALES;
import static com.ticketon.domain.event.EventStatusFixtures.EVENT_ID;
import static com.ticketon.domain.event.EventStatusFixtures.MARCH_14;
import static com.ticketon.domain.event.EventStatusFixtures.MARCH_21;
import static com.ticketon.domain.event.EventStatusFixtures.MARCH_22;
import static com.ticketon.domain.event.EventStatusFixtures.cancelledSinglePerformance;
import static com.ticketon.domain.event.EventStatusFixtures.consistentMomentFor;
import static com.ticketon.domain.event.EventStatusFixtures.eightPerformances;
import static com.ticketon.domain.event.EventStatusFixtures.korean;
import static com.ticketon.domain.event.EventStatusFixtures.lateSummerFestival;
import static com.ticketon.domain.event.EventStatusFixtures.singlePerformance;
import static com.ticketon.domain.event.EventStatusFixtures.singlePerformanceWithoutEndTime;
import static com.ticketon.domain.event.EventStatusFixtures.springPlayIn;
import static com.ticketon.domain.event.EventStatusFixtures.withOpensAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.common.KoreanTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("행사 상태 전환 — 판매 시작 · 시간 기준 전환 · 정산 완료 · 회차 취소 (docs/04 \"전환 트리거\", \"시간이 운영 판단을 이깁니다\")")
class EventTransitionTest {

    private static Instant koreanAt(String localDateTime) {
        return KoreanTime.toInstant(LocalDateTime.parse(localDateTime));
    }

    /** 일정과 지금 시각이 그 상태와 맞는 행사에 대해 — 취소됨은 회차가 취소된 일정, 나머지는 공연 하나. */
    private static EventSchedule consistentScheduleFor(EventStatus status) {
        return status == EventStatus.CANCELLED ? cancelledSinglePerformance() : singlePerformance();
    }

    @Nested
    @DisplayName("전환 표 — 사람이 누르는 전환을 상태 8종 모두에 대어 본다")
    class TransitionTable {

        @Test
        @DisplayName("판매 시작: 작성 중 → 판매 대기")
        void startSalesFromDraft() {
            Event event = springPlayIn(EventStatus.DRAFT);

            event.startSales(singlePerformance(), false, BEFORE_OPEN);

            assertThat(event.status()).isEqualTo(EventStatus.AWAITING_OPEN);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EventStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "DRAFT")
        @DisplayName("판매 시작: 작성 중이 아니면 막는다")
        void startSalesRejectedOutsideDraft(EventStatus status) {
            Event event = springPlayIn(status);

            assertRejected(() -> event.startSales(consistentScheduleFor(status), false, consistentMomentFor(status)),
                    EventErrorCode.SALES_ALREADY_STARTED);
            assertThat(event.status()).isEqualTo(status);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EventStatus.class, names = {"ENDED", "CANCELLED"})
        @DisplayName("정산 완료: 행사 종료 · 취소됨 → 정산 완료 (관리자)")
        void completeSettlementFromEndedOrCancelled(EventStatus status) {
            Event event = springPlayIn(status);

            event.completeSettlement(consistentScheduleFor(status), consistentMomentFor(status));

            assertThat(event.status()).isEqualTo(EventStatus.SETTLED);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EventStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"ENDED", "CANCELLED"})
        @DisplayName("정산 완료: 행사 종료 · 취소됨이 아니면 막는다")
        void completeSettlementRejectedOtherwise(EventStatus status) {
            Event event = springPlayIn(status);

            assertRejected(() -> event.completeSettlement(consistentScheduleFor(status), consistentMomentFor(status)),
                    EventErrorCode.SETTLEMENT_NOT_AVAILABLE);
            assertThat(event.status()).isEqualTo(status);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EventStatus.class, names = {"AWAITING_OPEN", "ON_SALE", "SALES_PAUSED", "SALES_CLOSED"})
        @DisplayName("남은 회차 전부 취소: 판매 대기 · 판매 중 · 판매 중단 · 판매 종료 → 공연한 회차가 없으면 취소됨")
        void allSessionsCancelledFromCancellableStatuses(EventStatus status) {
            Event event = springPlayIn(status);

            assertThat(event.reflectSessionCancellation(cancelledSinglePerformance(), consistentMomentFor(status))).isTrue();

            assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EventStatus.class, names = {"DRAFT", "ENDED", "SETTLED", "CANCELLED"})
        @DisplayName("남은 회차 전부 취소: 작성 중이거나 이미 끝난 행사는 막는다")
        void sessionCancellationRejectedOtherwise(EventStatus status) {
            Event event = springPlayIn(status);

            assertRejected(() -> event.reflectSessionCancellation(cancelledSinglePerformance(), consistentMomentFor(status)),
                    EventErrorCode.EVENT_NOT_CANCELLABLE);
            assertThat(event.status()).isEqualTo(status);
        }
    }

    @Nested
    @DisplayName("판매 시작 — 작성 중 → 판매 대기, 곧바로 지금 시각을 반영")
    class StartSales {

        @Test
        @DisplayName("오픈 시각을 비워 두면 판매를 시작하는 순간 판매 중이다")
        void opensImmediatelyWithoutOpeningTime() {
            Event event = springPlayIn(EventStatus.DRAFT, withOpensAt(null));

            event.startSales(singlePerformance(), false, BEFORE_OPEN);

            assertThat(event.status()).isEqualTo(EventStatus.ON_SALE);
        }

        @Test
        @DisplayName("오픈 시각이 이미 지났으면 곧바로 판매 중이다")
        void opensImmediatelyWhenOpeningTimePassed() {
            Event event = springPlayIn(EventStatus.DRAFT);

            event.startSales(singlePerformance(), false, DURING_SALES);

            assertThat(event.status()).isEqualTo(EventStatus.ON_SALE);
        }

        @Test
        @DisplayName("주최자가 신규 개설 차단(정지 1단계) 중이면 작성 중 행사의 판매를 시작할 수 없다 (docs/01 \"정지 4단계\")")
        void blockedBySuspension() {
            Event event = springPlayIn(EventStatus.DRAFT);

            assertRejected(() -> event.startSales(singlePerformance(), true, BEFORE_OPEN),
                    EventErrorCode.SALES_START_BLOCKED_BY_SUSPENSION);
            assertThat(event.status()).isEqualTo(EventStatus.DRAFT);
        }

        @Test
        @DisplayName("저장 전 행사에는 일정이 없고, 다른 행사의 일정은 받지 않는다")
        void requiresOwnSchedule() {
            Event unsaved = Event.create(TWELVE_MONTHS_ORGANIZER, ADDRESS, twelveMonthsSpringPlay());
            EventSchedule otherEventSchedule = EventSchedule.timed(EVENT_ID + 1);

            assertThatIllegalStateException().isThrownBy(() -> unsaved.startSales(singlePerformance(), false, BEFORE_OPEN));
            assertThatIllegalArgumentException().isThrownBy(
                    () -> springPlayIn(EventStatus.DRAFT).startSales(otherEventSchedule, false, BEFORE_OPEN));
        }
    }

    @Nested
    @DisplayName("시간 기준 전환 — 오픈 시각 · 마지막 회차의 예매 종료 · 종료 시각을 보고 한 번에 맞는 상태로")
    class AdvanceByTime {

        @ParameterizedTest(name = "{0} @ {1} → {2}")
        @CsvSource({
                "AWAITING_OPEN, 2026-03-01T13:59:59, AWAITING_OPEN",
                "AWAITING_OPEN, 2026-03-01T14:00:00, ON_SALE",
                "AWAITING_OPEN, 2026-03-14T18:00:00, SALES_CLOSED",
                "AWAITING_OPEN, 2026-03-14T21:00:00, ENDED",
                "ON_SALE,       2026-03-14T17:59:59, ON_SALE",
                "ON_SALE,       2026-03-14T18:00:00, SALES_CLOSED",
                "ON_SALE,       2026-03-14T21:00:00, ENDED",
                "SALES_PAUSED,  2026-03-14T17:59:59, SALES_PAUSED",
                "SALES_PAUSED,  2026-03-14T18:00:00, SALES_CLOSED",
                "SALES_PAUSED,  2026-03-14T21:00:00, ENDED",
                "SALES_CLOSED,  2026-03-14T20:59:59, SALES_CLOSED",
                "SALES_CLOSED,  2026-03-14T21:00:00, ENDED"
        })
        @DisplayName("공연 하나(3/14 19:00~21:00, 3/1 14:00 오픈)의 경계 시각 — 그 순간부터 넘어간다")
        void boundaries(EventStatus from, String moment, EventStatus expected) {
            Event event = springPlayIn(from);

            event.advanceByTime(singlePerformance(), koreanAt(moment));

            assertThat(event.status()).isEqualTo(expected);
        }

        @Test
        @DisplayName("18:00 예매 마감 → 판매 종료, 19:00 공연 시작에도 판매 종료, 21:00 공연 끝 → 행사 종료 (docs/04 예시)")
        void singlePerformanceDay() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = singlePerformance();

            assertThat(event.advanceByTime(schedule, korean(MARCH_14, 18, 0))).isTrue();
            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);

            assertThat(event.advanceByTime(schedule, korean(MARCH_14, 19, 0))).isFalse();
            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);

            assertThat(event.advanceByTime(schedule, korean(MARCH_14, 21, 0))).isTrue();
            assertThat(event.status()).isEqualTo(EventStatus.ENDED);
        }

        @Test
        @DisplayName("판매 중단 중에 마지막 회차의 예매 종료 시각이 지나면 판매 종료 — 시간이 이긴다")
        void timeBeatsPause() {
            Event event = springPlayIn(EventStatus.SALES_PAUSED);

            event.advanceByTime(singlePerformance(), korean(MARCH_14, 18, 0));

            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EventStatus.class, names = {"DRAFT", "ENDED", "SETTLED", "CANCELLED"})
        @DisplayName("작성 중 · 행사 종료 · 정산 완료 · 취소됨은 시간이 지나도 그대로다")
        void statusesNotMovedByTime(EventStatus status) {
            Event event = springPlayIn(status);

            assertThat(event.advanceByTime(singlePerformance(), korean(2027, 1, 1, 0, 0))).isFalse();

            assertThat(event.status()).isEqualTo(status);
        }

        @Test
        @DisplayName("오픈 시각을 비워 둔 판매 대기는 곧바로 판매 중이다")
        void awaitingWithoutOpeningTimeOpens() {
            Event event = springPlayIn(EventStatus.AWAITING_OPEN, withOpensAt(null));

            event.advanceByTime(singlePerformance(), BEFORE_OPEN);

            assertThat(event.status()).isEqualTo(EventStatus.ON_SALE);
        }

        @Test
        @DisplayName("마지막 회차의 종료 시각이 비어 있으면 행사 종료를 판정할 수 없어 판매 종료에 머문다")
        void staysSalesClosedWithoutEndTime() {
            Event event = springPlayIn(EventStatus.ON_SALE);

            event.advanceByTime(singlePerformanceWithoutEndTime(), AFTER_EVENT_ENDED);

            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);
        }

        @Test
        @DisplayName("8회차 공연에서 3회차가 끝나고 5회차가 남았으면 행사는 여전히 판매 중이다 (docs/04 \"행사 상태와 회차 상태는 다릅니다\")")
        void stillOnSaleWhileSessionsRemain() {
            Event event = springPlayIn(EventStatus.ON_SALE);

            assertThat(event.advanceByTime(eightPerformances(Set.of()), korean(LocalDate.of(2026, 3, 15), 17, 30))).isFalse();

            assertThat(event.status()).isEqualTo(EventStatus.ON_SALE);
        }

        @Test
        @DisplayName("8회차의 판매 종료는 마지막 회차(3/22 19:00)의 예매 종료 18:00, 행사 종료는 21:00")
        void eightPerformancesEndWithLastSession() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = eightPerformances(Set.of());

            event.advanceByTime(schedule, korean(MARCH_22, 18, 0).minusSeconds(1));
            assertThat(event.status()).isEqualTo(EventStatus.ON_SALE);

            event.advanceByTime(schedule, korean(MARCH_22, 18, 0));
            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);

            event.advanceByTime(schedule, korean(MARCH_22, 21, 0));
            assertThat(event.status()).isEqualTo(EventStatus.ENDED);
        }

        @Test
        @DisplayName("마지막 회차는 취소되지 않은 회차 중 마지막 — 3/22 저녁이 취소되면 3/22 15:00 회차로 14:00 판매 종료, 17:00 행사 종료")
        void cancelledLastSessionIsNotWaitedFor() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = eightPerformances(Set.of(108L));

            event.advanceByTime(schedule, korean(MARCH_22, 14, 0));
            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);

            event.advanceByTime(schedule, korean(MARCH_22, 17, 0));
            assertThat(event.status()).isEqualTo(EventStatus.ENDED);
        }

        @Test
        @DisplayName("종일형 페스티벌은 마지막 날 마지막 입장(비우면 운영 종료 18:00) 1시간 전 17:00에 판매 종료, 18:00에 행사 종료")
        void allDayFestival() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = lateSummerFestival();
            LocalDate lastDay = LocalDate.of(2026, 5, 5);

            event.advanceByTime(schedule, korean(lastDay, 17, 0).minusSeconds(1));
            assertThat(event.status()).isEqualTo(EventStatus.ON_SALE);

            event.advanceByTime(schedule, korean(lastDay, 17, 0));
            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);

            event.advanceByTime(schedule, korean(lastDay, 18, 0));
            assertThat(event.status()).isEqualTo(EventStatus.ENDED);
        }

        @Test
        @DisplayName("뒤로 가지 않는다 — 판매 종료 뒤 판매 종료 시각이 늦어진 일정을 받아도 판매 중으로 되돌리지 않는다")
        void neverMovesBackward() {
            Event event = springPlayIn(EventStatus.SALES_CLOSED);

            assertThat(event.advanceByTime(eightPerformances(Set.of()), korean(MARCH_14, 18, 30))).isFalse();

            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);
        }

        @Test
        @DisplayName("지금 시각이 정하는 상태는 계산만 하고 저장된 상태를 바꾸지 않는다")
        void statusAtDoesNotChangeEvent() {
            Event event = springPlayIn(EventStatus.ON_SALE);

            assertThat(event.statusAt(singlePerformance(), AFTER_EVENT_ENDED)).isEqualTo(EventStatus.ENDED);
            assertThat(event.status()).isEqualTo(EventStatus.ON_SALE);
        }
    }

    @Nested
    @DisplayName("정산 완료")
    class CompleteSettlement {

        @Test
        @DisplayName("판매 종료로 저장돼 있어도 행사 종료 시각이 지났으면 행사 종료를 반영한 뒤 정산 완료한다")
        void advancesBeforeSettling() {
            Event event = springPlayIn(EventStatus.SALES_CLOSED);

            event.completeSettlement(singlePerformance(), AFTER_EVENT_ENDED);

            assertThat(event.status()).isEqualTo(EventStatus.SETTLED);
        }

        @Test
        @DisplayName("행사 종료 시각 전의 판매 종료는 정산 완료할 수 없다")
        void rejectedBeforeEventEnds() {
            Event event = springPlayIn(EventStatus.SALES_CLOSED);

            assertRejected(() -> event.completeSettlement(singlePerformance(), korean(MARCH_14, 20, 0)),
                    EventErrorCode.SETTLEMENT_NOT_AVAILABLE);
        }
    }

    @Nested
    @DisplayName("회차 취소 반영 — 남은 회차 전부 취소 → 취소됨 또는 행사 종료 (docs/04 \"남은 회차가 없어지면\", \"행사 취소\")")
    class SessionCancellation {

        private static final Set<Long> REMAINING_THREE = Set.of(106L, 107L, 108L);

        @Test
        @DisplayName("공연한 회차 없이 8회차를 전부 취소하면 취소됨")
        void allCancelledBeforeAnyPerformance() {
            Event event = springPlayIn(EventStatus.ON_SALE);

            event.reflectSessionCancellation(eightPerformances(Set.of(101L, 102L, 103L, 104L, 105L, 106L, 107L, 108L)), DURING_SALES);

            assertThat(event.status()).isEqualTo(EventStatus.CANCELLED);
        }

        @Test
        @DisplayName("8회차 중 5회차를 공연한 뒤 남은 3회차를 취소하면 행사 종료 — 5회차분은 정산된다 (docs/04 예시)")
        void remainingCancelledAfterFivePerformances() {
            Event event = springPlayIn(EventStatus.ON_SALE);

            event.reflectSessionCancellation(eightPerformances(REMAINING_THREE), korean(MARCH_21, 17, 30));

            assertThat(event.status()).isEqualTo(EventStatus.ENDED);
        }

        @Test
        @DisplayName("7회차를 이미 공연했다면 마지막 회차를 취소해도 취소됨이 아니라 행사 종료")
        void lastCancelledAfterSevenPerformances() {
            Event event = springPlayIn(EventStatus.ON_SALE);

            event.reflectSessionCancellation(eightPerformances(Set.of(108L)), korean(MARCH_22, 17, 30));

            assertThat(event.status()).isEqualTo(EventStatus.ENDED);
        }

        @Test
        @DisplayName("공연한 마지막 회차가 아직 진행 중이면 판매 종료에 머물다 그 회차가 끝날 때 행사 종료")
        void endsWhenPerformedSessionEnds() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            EventSchedule schedule = eightPerformances(REMAINING_THREE);

            event.reflectSessionCancellation(schedule, korean(MARCH_21, 16, 0));
            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);

            event.advanceByTime(schedule, korean(MARCH_21, 17, 0));
            assertThat(event.status()).isEqualTo(EventStatus.ENDED);
        }

        @Test
        @DisplayName("3/15 저녁 회차 하나만 취소하면 나머지 회차가 남아 행사는 그대로 판매 중")
        void oneSessionCancelled() {
            Event event = springPlayIn(EventStatus.ON_SALE);

            assertThat(event.reflectSessionCancellation(eightPerformances(Set.of(104L)), korean(2026, 3, 12, 12, 0))).isFalse();

            assertThat(event.status()).isEqualTo(EventStatus.ON_SALE);
        }

        @Test
        @DisplayName("마지막 회차를 취소하면 판매 종료 시각이 그 앞 회차로 앞당겨진다")
        void cancellingLastSessionBringsSalesEndForward() {
            Event event = springPlayIn(EventStatus.ON_SALE);

            event.reflectSessionCancellation(eightPerformances(Set.of(108L)), korean(MARCH_22, 16, 0));

            assertThat(event.status()).isEqualTo(EventStatus.SALES_CLOSED);
        }

        @Test
        @DisplayName("회차를 지운(취소가 아닌) 빈 일정은 취소됨으로 보지 않는다")
        void emptyScheduleIsNotCancellation() {
            Event event = springPlayIn(EventStatus.AWAITING_OPEN);
            EventSchedule empty = EventSchedule.restore(EVENT_ID, SessionMode.TIMED, null, List.of());

            assertThat(event.reflectSessionCancellation(empty, BEFORE_OPEN)).isFalse();

            assertThat(event.status()).isEqualTo(EventStatus.AWAITING_OPEN);
        }
    }
}
