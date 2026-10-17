package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.TWELVE_MONTHS_ORGANIZER;
import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static com.ticketon.domain.event.EventFixtures.twelveMonthsSpringPlay;
import static com.ticketon.domain.event.EventStatusFixtures.ADDRESS;
import static com.ticketon.domain.event.EventStatusFixtures.EVENT_ID;
import static com.ticketon.domain.event.EventStatusFixtures.LAST_MODIFIED_AT;
import static com.ticketon.domain.event.EventStatusFixtures.korean;
import static com.ticketon.domain.event.EventStatusFixtures.springPlayIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ticketon.domain.error.NotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * 위저드 3단계의 구역 저장과 6단계의 입구 저장 — 구역은 가격이 붙는 재고 묶음이고(docs/02 "구역"), 입구 목록은 도면에 속하지만 판매가 끝난 뒤에도
 * 고친다(docs/04 "판매 종료는 공연이 끝난 때가 아닙니다"). 저장할 때마다 바뀌는 항목의 수정 범위를 보고, 진행 단계 · 마지막 수정 시각을 맡는다.
 * 극단 열두달(docs/15 박정훈)의 봄 정기공연과 문서의 예시 — "R석, 5만원"(docs/02 "두 개의 축"), "스탠딩A 300 / 스탠딩B 500"(docs/02 "구역"),
 * "정문", "2층 전용", "VIP 입구"(docs/02 "입구 (게이트)"), 18:10의 "옆문"(docs/04).
 */
@DisplayName("위저드 3단계 구역 · 6단계 입구 저장 (docs/02 \"구역\" · \"입구 (게이트)\", docs/04 \"상태별로 할 수 있는 것\")")
class EventZoneAndEntranceStepTest {

    /** 3단계에서 구역을 저장한 시각 — 2/12 20:00. */
    private static final Instant SAVED_AT = korean(LocalDate.of(2026, 2, 12), 20, 0);

    /** 그다음 저장 — 2/13 09:00. */
    private static final Instant SAVED_AGAIN_AT = korean(LocalDate.of(2026, 2, 13), 9, 0);

    private static final long R_SEAT_ZONE = 1L;
    private static final long STANDING_A_ZONE = 2L;
    private static final long MAIN_GATE = 1L;
    private static final ZoneColor RED = ZoneColor.of("#E5484D");
    private static final ZoneColor GREEN = ZoneColor.of("#30A46C");

    private static final ZoneSettings R_SEAT = ZoneSettings.designated(ZoneName.of("R석"), RED, ZoneBasePrice.of(50_000));
    private static final ZoneSettings STANDING_A_300 =
            ZoneSettings.quantity(ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(30_000), ZoneCapacity.of(300));

    /** 1단계까지만 저장한 작성 중 봄 정기공연. */
    private static Event draftAtFirstStep() {
        return Event.restore(EVENT_ID, TWELVE_MONTHS_ORGANIZER, ADDRESS, twelveMonthsSpringPlay(), null,
                EventVisibilitySetting.publicByDefault(), BookingSettings.defaults(), CancellationPolicy.standard(),
                EventStatus.DRAFT, null, List.of(), EventWizardStep.BASIC_INFORMATION, LAST_MODIFIED_AT);
    }

    /** 구역 R석(지정형, 5만원) · 스탠딩A(수량형 300, 3만원)이 있는 도면. */
    private static FloorPlan rSeatAndStandingA() {
        return FloorPlan.restore(EVENT_ID, List.of(
                Zone.restore(R_SEAT_ZONE, ZoneKind.DESIGNATED, R_SEAT.name(), R_SEAT.color(), R_SEAT.basePrice(), null),
                Zone.restore(STANDING_A_ZONE, ZoneKind.QUANTITY, STANDING_A_300.name(), STANDING_A_300.color(),
                        STANDING_A_300.basePrice(), STANDING_A_300.capacity())),
                List.of(), null);
    }

    /** 입구 "정문"이 있는 목록. */
    private static EventEntrances mainGateOnly() {
        return EventEntrances.restore(EVENT_ID, List.of(Entrance.restore(MAIN_GATE, EntranceName.of("정문"))));
    }

    private static List<String> zoneNamesOf(FloorPlan floorPlan) {
        return floorPlan.zones().stream().map(zone -> zone.name().value()).toList();
    }

    private static List<String> entranceNamesOf(EventEntrances entrances) {
        return entrances.entrances().stream().map(entrance -> entrance.name().value()).toList();
    }

    @Nested
    @DisplayName("3단계 — 구역 더하기")
    class AddZone {

        @Test
        @DisplayName("수량형 '스탠딩A 300'과 지정형 'R석 5만원'을 더하면 도면에 생기고, 진행 단계가 3단계 · 마지막 수정 시각이 저장한 시각이 된다")
        void addsQuantityAndDesignatedZones() {
            Event event = draftAtFirstStep();
            FloorPlan floorPlan = FloorPlan.empty(EVENT_ID);

            Zone standing = event.addZone(floorPlan, ZoneKind.QUANTITY, STANDING_A_300, SAVED_AT);
            Zone rSeat = event.addZone(floorPlan, ZoneKind.DESIGNATED, R_SEAT, SAVED_AGAIN_AT);

            assertThat(standing.id()).isEmpty();
            assertThat(standing.kind()).isEqualTo(ZoneKind.QUANTITY);
            assertThat(standing.capacity()).contains(ZoneCapacity.of(300));
            assertThat(standing.basePrice()).isEqualTo(ZoneBasePrice.of(30_000));
            assertThat(rSeat.kind()).isEqualTo(ZoneKind.DESIGNATED);
            assertThat(rSeat.capacity()).isEmpty();
            assertThat(rSeat.basePrice()).isEqualTo(ZoneBasePrice.of(50_000));
            assertThat(zoneNamesOf(floorPlan)).containsExactly("스탠딩A", "R석");
            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.SEATING);
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AGAIN_AT);
        }

        @Test
        @DisplayName("6단계까지 저장한 행사에 구역을 더해도 진행 단계는 내려가지 않는다")
        void progressDoesNotGoBack() {
            Event event = springPlayIn(EventStatus.DRAFT);

            event.addZone(FloorPlan.empty(EVENT_ID), ZoneKind.QUANTITY, STANDING_A_300, SAVED_AT);

            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS);
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("수량형인데 정원이 없거나 지정형인데 정원이 있으면 막고 아무것도 바꾸지 않는다")
        void capacityMustMatchKind() {
            Event event = draftAtFirstStep();
            FloorPlan floorPlan = FloorPlan.empty(EVENT_ID);

            assertRejected(() -> event.addZone(floorPlan, ZoneKind.QUANTITY,
                    new ZoneSettings(ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(30_000), null), SAVED_AT),
                    EventErrorCode.ZONE_CAPACITY_REQUIRED);
            assertRejected(() -> event.addZone(floorPlan, ZoneKind.DESIGNATED,
                    new ZoneSettings(ZoneName.of("R석"), RED, ZoneBasePrice.of(50_000), ZoneCapacity.of(120)), SAVED_AT),
                    EventErrorCode.ZONE_CAPACITY_ONLY_FOR_QUANTITY);

            assertThat(floorPlan.zones()).isEmpty();
            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
            assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
        }

        @Test
        @DisplayName("구역 이름은 블록 이름과 겹칠 수 없다 — 블록 '1층'이 있으면 구역 '1층'을 막는다")
        void zoneNameCannotTakeBlockName() {
            Event event = draftAtFirstStep();
            FloorPlan floorPlan = FloorPlan.empty(EVENT_ID);
            floorPlan.addBlock(BlockName.of("1층"), BlockSize.of(5, 10), CanvasPoint.ORIGIN, BlockAngle.NONE);

            assertRejected(() -> event.addZone(floorPlan, ZoneKind.DESIGNATED,
                    ZoneSettings.designated(ZoneName.of("1층"), RED, ZoneBasePrice.of(50_000)), SAVED_AT),
                    EventErrorCode.ZONE_NAME_TAKEN_BY_BLOCK);
            assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
        }

        @Test
        @DisplayName("예매가 열린 뒤에도 구역을 더할 수 있다 — 도면은 규칙(#64)을 지켜 고친다")
        void addsAfterBookingOpened() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            FloorPlan floorPlan = rSeatAndStandingA();

            event.addZone(floorPlan, ZoneKind.QUANTITY,
                    ZoneSettings.quantity(ZoneName.of("스탠딩B"), GREEN, ZoneBasePrice.of(25_000), ZoneCapacity.of(500)), SAVED_AT);

            assertThat(zoneNamesOf(floorPlan)).containsExactly("R석", "스탠딩A", "스탠딩B");
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("판매가 끝나면 도면이 고정되어 구역을 더할 수 없고, 지난 행사는 전부 고정이다")
        void lockedAfterSalesClosed() {
            assertRejected(() -> springPlayIn(EventStatus.SALES_CLOSED).addZone(FloorPlan.empty(EVENT_ID), ZoneKind.QUANTITY,
                    STANDING_A_300, SAVED_AT), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertRejected(() -> springPlayIn(EventStatus.ENDED).addZone(FloorPlan.empty(EVENT_ID), ZoneKind.QUANTITY,
                    STANDING_A_300, SAVED_AT), EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT);
        }

        @Test
        @DisplayName("다른 행사의 도면은 받지 않는다")
        void rejectsOtherEventsFloorPlan() {
            assertThatIllegalArgumentException().isThrownBy(() -> draftAtFirstStep().addZone(FloorPlan.empty(EVENT_ID + 1),
                    ZoneKind.QUANTITY, STANDING_A_300, SAVED_AT));
        }
    }

    @Nested
    @DisplayName("3단계 — 구역 한 줄 고치기")
    class ChangeZone {

        @Test
        @DisplayName("같은 칸을 다시 저장하면 바뀌는 것이 없어 false — 6단계까지 저장한 행사의 마지막 수정 시각도 그대로다")
        void sameValuesChangeNothing() {
            Event event = springPlayIn(EventStatus.DRAFT);
            FloorPlan floorPlan = rSeatAndStandingA();

            assertThat(event.changeZone(floorPlan, STANDING_A_ZONE, STANDING_A_300, SAVED_AT)).isFalse();

            assertThat(floorPlan.zone(STANDING_A_ZONE).settings()).isEqualTo(STANDING_A_300);
            assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
        }

        @Test
        @DisplayName("스탠딩A의 정원을 300에서 500으로, 기본가를 3만원에서 3만 5천원으로 고치면 true — 마지막 수정 시각이 저장한 시각이 된다")
        void changesCapacityAndBasePrice() {
            Event event = springPlayIn(EventStatus.DRAFT);
            FloorPlan floorPlan = rSeatAndStandingA();
            ZoneSettings enlarged = ZoneSettings.quantity(ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(35_000), ZoneCapacity.of(500));

            assertThat(event.changeZone(floorPlan, STANDING_A_ZONE, enlarged, SAVED_AT)).isTrue();

            assertThat(floorPlan.zone(STANDING_A_ZONE).capacity()).contains(ZoneCapacity.of(500));
            assertThat(floorPlan.zone(STANDING_A_ZONE).basePrice()).isEqualTo(ZoneBasePrice.of(35_000));
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("작성 중 1단계에 머문 행사는 같은 칸을 저장해도 진행 단계가 3단계로 올라 true다")
        void progressesEvenWithSameValues() {
            Event event = draftAtFirstStep();

            assertThat(event.changeZone(rSeatAndStandingA(), R_SEAT_ZONE, R_SEAT, SAVED_AT)).isTrue();

            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.SEATING);
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("예매가 열린 뒤에도 기본가 · 이름 · 정원은 고친다 — 기본가는 신규 예매부터, 정원은 늘리기만(#64)")
        void changesAfterBookingOpened() {
            Event event = springPlayIn(EventStatus.ON_SALE);
            FloorPlan floorPlan = rSeatAndStandingA();
            ZoneSettings renamedAndRepriced =
                    ZoneSettings.quantity(ZoneName.of("스탠딩 A"), GREEN, ZoneBasePrice.of(33_000), ZoneCapacity.of(350));

            assertThat(event.changeZone(floorPlan, STANDING_A_ZONE, renamedAndRepriced, SAVED_AT)).isTrue();

            assertThat(floorPlan.zone(STANDING_A_ZONE).settings()).isEqualTo(renamedAndRepriced);
        }

        @Test
        @DisplayName("판매가 끝난 뒤에는 바뀌는 칸이 있으면 막고 아무것도 바꾸지 않는다 — 바뀌는 칸이 없으면 막지 않는다")
        void lockedAfterSalesClosedOnlyWhenChanged() {
            Event event = springPlayIn(EventStatus.SALES_CLOSED);
            FloorPlan floorPlan = rSeatAndStandingA();
            ZoneSettings repriced = ZoneSettings.designated(ZoneName.of("R석"), RED, ZoneBasePrice.of(55_000));

            assertRejected(() -> event.changeZone(floorPlan, R_SEAT_ZONE, repriced, SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);
            assertThat(event.changeZone(floorPlan, R_SEAT_ZONE, R_SEAT, SAVED_AT)).isFalse();

            assertThat(floorPlan.zone(R_SEAT_ZONE).settings()).isEqualTo(R_SEAT);
            assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
        }

        @Test
        @DisplayName("지정형 구역에 정원을 보내거나 수량형 구역에서 정원을 비우면 막는다 — 지정형의 자리는 배정한 좌석이다")
        void capacityMustMatchStoredKind() {
            Event event = springPlayIn(EventStatus.DRAFT);
            FloorPlan floorPlan = rSeatAndStandingA();

            assertRejected(() -> event.changeZone(floorPlan, R_SEAT_ZONE,
                    new ZoneSettings(ZoneName.of("R석"), RED, ZoneBasePrice.of(50_000), ZoneCapacity.of(120)), SAVED_AT),
                    EventErrorCode.ZONE_CAPACITY_ONLY_FOR_QUANTITY);
            assertRejected(() -> event.changeZone(floorPlan, STANDING_A_ZONE,
                    new ZoneSettings(ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(30_000), null), SAVED_AT),
                    EventErrorCode.ZONE_CAPACITY_REQUIRED);
        }

        @Test
        @DisplayName("다른 구역이 쓰는 이름으로 고치면 막고, 함께 보낸 기본가도 바꾸지 않는다")
        void takenNameChangesNothing() {
            Event event = springPlayIn(EventStatus.DRAFT);
            FloorPlan floorPlan = rSeatAndStandingA();

            assertRejected(() -> event.changeZone(floorPlan, R_SEAT_ZONE,
                    ZoneSettings.designated(ZoneName.of("스탠딩A"), RED, ZoneBasePrice.of(55_000)), SAVED_AT),
                    EventErrorCode.ZONE_NAME_ALREADY_TAKEN);

            assertThat(floorPlan.zone(R_SEAT_ZONE).settings()).isEqualTo(R_SEAT);
            assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
        }

        @Test
        @DisplayName("도면에 없는 구역이면 찾을 수 없다")
        void unknownZone() {
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> springPlayIn(EventStatus.DRAFT).changeZone(rSeatAndStandingA(), 99L, R_SEAT, SAVED_AT))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(EventErrorCode.ZONE_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("3단계 — 구역 지우기")
    class RemoveZone {

        @Test
        @DisplayName("구역을 지우면 지운 구역을 돌려주고 도면에서 빠진다 — 마지막 수정 시각이 지운 시각이 된다")
        void removesZone() {
            Event event = springPlayIn(EventStatus.DRAFT);
            FloorPlan floorPlan = rSeatAndStandingA();

            Zone removed = event.removeZone(floorPlan, STANDING_A_ZONE, SAVED_AT);

            assertThat(removed.id()).hasValue(STANDING_A_ZONE);
            assertThat(removed.kind()).isEqualTo(ZoneKind.QUANTITY);
            assertThat(zoneNamesOf(floorPlan)).containsExactly("R석");
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("판매가 끝나면 구역을 지울 수 없고 도면은 그대로다")
        void lockedAfterSalesClosed() {
            Event event = springPlayIn(EventStatus.SALES_CLOSED);
            FloorPlan floorPlan = rSeatAndStandingA();

            assertRejected(() -> event.removeZone(floorPlan, STANDING_A_ZONE, SAVED_AT), EventErrorCode.EDIT_LOCKED_AFTER_SALES_CLOSED);

            assertThat(zoneNamesOf(floorPlan)).containsExactly("R석", "스탠딩A");
        }

        @Test
        @DisplayName("도면에 없는 구역은 찾을 수 없다")
        void unknownZone() {
            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> springPlayIn(EventStatus.DRAFT).removeZone(rSeatAndStandingA(), 99L, SAVED_AT))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(EventErrorCode.ZONE_NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("6단계 — 입구")
    class Entrances {

        @Test
        @DisplayName("'정문' · '2층 전용' · 'VIP 입구'를 더하면 목록에 더한 순서로 생기고, 진행 단계가 6단계가 된다")
        void addsThreeEntrances() {
            Event event = draftAtFirstStep();
            EventEntrances entrances = EventEntrances.empty(EVENT_ID);

            event.addEntrance(entrances, EntranceName.of("정문"), SAVED_AT);
            event.addEntrance(entrances, EntranceName.of("2층 전용"), SAVED_AT);
            Entrance vip = event.addEntrance(entrances, EntranceName.of("VIP 입구"), SAVED_AGAIN_AT);

            assertThat(vip.id()).isEmpty();
            assertThat(entranceNamesOf(entrances)).containsExactly("정문", "2층 전용", "VIP 입구");
            assertThat(event.wizardProgress()).isEqualTo(EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS);
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AGAIN_AT);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = EventStatus.class, names = {"AWAITING_OPEN", "ON_SALE", "SALES_PAUSED", "SALES_CLOSED"})
        @DisplayName("판매 시작 뒤에도, 판매가 끝난 뒤에도 입구를 더하고 이름을 바꾸고 지운다 — 18:10 정문 줄이 길어 '옆문'을 더한다")
        void editableUntilEventEnds(EventStatus status) {
            Event event = springPlayIn(status);
            EventEntrances entrances = mainGateOnly();

            event.addEntrance(entrances, EntranceName.of("옆문"), SAVED_AT);
            event.renameEntrance(entrances, MAIN_GATE, EntranceName.of("정문(매표소 쪽)"), SAVED_AT);
            event.removeEntrance(entrances, MAIN_GATE, SAVED_AT);

            assertThat(entranceNamesOf(entrances)).containsExactly("옆문");
            assertThat(event.lastModifiedAt()).isEqualTo(SAVED_AT);
        }

        @Test
        @DisplayName("지난 행사는 입구도 고정이다")
        void lockedForPastEvent() {
            Event event = springPlayIn(EventStatus.ENDED);
            EventEntrances entrances = mainGateOnly();

            assertRejected(() -> event.addEntrance(entrances, EntranceName.of("옆문"), SAVED_AT), EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT);
            assertRejected(() -> event.renameEntrance(entrances, MAIN_GATE, EntranceName.of("옆문"), SAVED_AT),
                    EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT);
            assertRejected(() -> event.removeEntrance(entrances, MAIN_GATE, SAVED_AT), EventErrorCode.EDIT_LOCKED_FOR_PAST_EVENT);

            assertThat(entranceNamesOf(entrances)).containsExactly("정문");
        }

        @Test
        @DisplayName("같은 이름으로 바꾸면 false — 지난 행사라도 막지 않고 아무것도 바꾸지 않는다")
        void sameNameChangesNothing() {
            Event event = springPlayIn(EventStatus.ENDED);

            assertThat(event.renameEntrance(mainGateOnly(), MAIN_GATE, EntranceName.of(" 정문 "), SAVED_AT)).isFalse();

            assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
        }

        @Test
        @DisplayName("같은 이름의 입구는 둘일 수 없다")
        void duplicateName() {
            Event event = springPlayIn(EventStatus.DRAFT);

            assertRejected(() -> event.addEntrance(mainGateOnly(), EntranceName.of("정문"), SAVED_AT),
                    EventErrorCode.ENTRANCE_NAME_ALREADY_TAKEN);
            assertThat(event.lastModifiedAt()).isEqualTo(LAST_MODIFIED_AT);
        }

        @Test
        @DisplayName("지운 입구를 돌려주고, 목록에 없는 입구는 찾을 수 없다")
        void removesAndRejectsUnknown() {
            Event event = springPlayIn(EventStatus.DRAFT);
            EventEntrances entrances = mainGateOnly();

            assertThatExceptionOfType(NotFoundException.class)
                    .isThrownBy(() -> event.removeEntrance(entrances, 99L, SAVED_AT))
                    .satisfies(exception -> assertThat(exception.errorCode()).isEqualTo(EventErrorCode.ENTRANCE_NOT_FOUND));
            Entrance removed = event.removeEntrance(entrances, MAIN_GATE, SAVED_AT);

            assertThat(removed.name()).isEqualTo(EntranceName.of("정문"));
            assertThat(entrances.entrances()).isEmpty();
        }

        @Test
        @DisplayName("다른 행사의 입구 목록은 받지 않는다")
        void rejectsOtherEventsEntrances() {
            assertThatIllegalArgumentException().isThrownBy(() -> draftAtFirstStep().addEntrance(EventEntrances.empty(EVENT_ID + 1),
                    EntranceName.of("정문"), SAVED_AT));
        }
    }
}
