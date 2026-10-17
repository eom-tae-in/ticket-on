package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.assertRejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * 구역 한 줄의 칸(이름 · 색 · 기본가 · 정원)으로 구역을 더하고 고치기 — 위저드 3단계가 한 줄의 칸을 모두 보낸다. 바뀌는 칸은 상태별 수정 범위 표의
 * 단위로 나누고(docs/04), 정원은 수량형에만 있다(docs/02 "설정 흐름"). 구역이 생기고 바뀌고 지워진 것을 재고에 알리는 값도 본다(#54).
 */
@DisplayName("구역 한 줄의 칸 — 더하기 · 고치기 · 바뀌는 항목 · 재고 알림 (docs/02 \"구역\", docs/04 \"예매가 열린 뒤 수정 범위\")")
class FloorPlanZoneSettingsTest {

    private static final long EVENT_ID = 7L;
    private static final long R_SEAT_ZONE = 1L;
    private static final long STANDING_A_ZONE = 2L;
    private static final ZoneColor RED = ZoneColor.of("#E5484D");
    private static final ZoneColor BLUE = ZoneColor.of("#3E63DD");
    private static final ZoneColor GREEN = ZoneColor.of("#30A46C");

    private static final ZoneSettings R_SEAT = ZoneSettings.designated(ZoneName.of("R석"), RED, ZoneBasePrice.of(50_000));
    private static final ZoneSettings STANDING_A_300 =
            ZoneSettings.quantity(ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(30_000), ZoneCapacity.of(300));

    private static FloorPlan rSeatAndStandingA() {
        return FloorPlan.restore(EVENT_ID, List.of(
                Zone.restore(R_SEAT_ZONE, ZoneKind.DESIGNATED, R_SEAT.name(), R_SEAT.color(), R_SEAT.basePrice(), null),
                Zone.restore(STANDING_A_ZONE, ZoneKind.QUANTITY, STANDING_A_300.name(), STANDING_A_300.color(),
                        STANDING_A_300.basePrice(), STANDING_A_300.capacity())),
                List.of(), null);
    }

    @Nested
    @DisplayName("바뀌는 항목")
    class ChangedEditTargets {

        @Test
        @DisplayName("같은 칸이면 비어 있다")
        void sameValues() {
            assertThat(rSeatAndStandingA().changedEditTargets(STANDING_A_ZONE, STANDING_A_300)).isEmpty();
        }

        @Test
        @DisplayName("이름이나 색은 도면, 기본가는 가격, 정원은 정원이다")
        void eachFieldMapsToItsTarget() {
            FloorPlan floorPlan = rSeatAndStandingA();

            assertThat(floorPlan.changedEditTargets(R_SEAT_ZONE,
                    ZoneSettings.designated(ZoneName.of("VIP석"), RED, ZoneBasePrice.of(50_000))))
                    .containsExactly(EventEditTarget.FLOOR_PLAN);
            assertThat(floorPlan.changedEditTargets(R_SEAT_ZONE,
                    ZoneSettings.designated(ZoneName.of("R석"), BLUE, ZoneBasePrice.of(50_000))))
                    .containsExactly(EventEditTarget.FLOOR_PLAN);
            assertThat(floorPlan.changedEditTargets(R_SEAT_ZONE,
                    ZoneSettings.designated(ZoneName.of("R석"), RED, ZoneBasePrice.of(60_000))))
                    .containsExactly(EventEditTarget.PRICE);
            assertThat(floorPlan.changedEditTargets(STANDING_A_ZONE,
                    ZoneSettings.quantity(ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(30_000), ZoneCapacity.of(500))))
                    .containsExactly(EventEditTarget.CAPACITY);
            assertThat(floorPlan.changedEditTargets(STANDING_A_ZONE,
                    ZoneSettings.quantity(ZoneName.of("스탠딩B"), GREEN, ZoneBasePrice.of(25_000), ZoneCapacity.of(500))))
                    .containsExactlyInAnyOrder(EventEditTarget.FLOOR_PLAN, EventEditTarget.PRICE, EventEditTarget.CAPACITY);
        }

        @Test
        @DisplayName("앞뒤 공백 · 소문자 색은 같은 값으로 맞춰 바뀐 것이 아니다")
        void normalizedValuesAreSame() {
            assertThat(rSeatAndStandingA().changedEditTargets(R_SEAT_ZONE,
                    ZoneSettings.designated(ZoneName.of(" R석 "), ZoneColor.of("#e5484d"), ZoneBasePrice.of(50_000)))).isEmpty();
        }

        @Test
        @DisplayName("정원이 구역 종류와 어긋나면 바뀌는 항목을 따지기 전에 막는다")
        void capacityMustMatchKind() {
            FloorPlan floorPlan = rSeatAndStandingA();

            assertRejected(() -> floorPlan.changedEditTargets(R_SEAT_ZONE,
                    new ZoneSettings(ZoneName.of("R석"), RED, ZoneBasePrice.of(50_000), ZoneCapacity.of(120))),
                    EventErrorCode.ZONE_CAPACITY_ONLY_FOR_QUANTITY);
            assertRejected(() -> floorPlan.changedEditTargets(STANDING_A_ZONE,
                    new ZoneSettings(ZoneName.of("스탠딩A"), GREEN, ZoneBasePrice.of(30_000), null)),
                    EventErrorCode.ZONE_CAPACITY_REQUIRED);
        }
    }

    @Nested
    @DisplayName("더하기 · 고치기")
    class AddAndChange {

        @Test
        @DisplayName("종류와 칸으로 구역을 더한다 — 수량형 '전체 200'(전시)")
        void addsByKind() {
            FloorPlan floorPlan = FloorPlan.empty(EVENT_ID);

            Zone whole = floorPlan.addZone(ZoneKind.QUANTITY,
                    ZoneSettings.quantity(ZoneName.of("전체"), GREEN, ZoneBasePrice.of(15_000), ZoneCapacity.of(200)));

            assertThat(whole.kind()).isEqualTo(ZoneKind.QUANTITY);
            assertThat(whole.capacity()).contains(ZoneCapacity.of(200));
            assertThat(floorPlan.zones()).containsExactly(whole);
        }

        @Test
        @DisplayName("칸을 모두 고친다 — 종류와 id는 그대로다")
        void changesAllFields() {
            FloorPlan floorPlan = rSeatAndStandingA();
            ZoneSettings standingB = ZoneSettings.quantity(ZoneName.of("스탠딩B"), BLUE, ZoneBasePrice.of(25_000), ZoneCapacity.of(500));

            assertThat(floorPlan.changeZone(STANDING_A_ZONE, standingB)).isTrue();
            assertThat(floorPlan.changeZone(STANDING_A_ZONE, standingB)).isFalse();

            Zone changed = floorPlan.zone(STANDING_A_ZONE);
            assertThat(changed.settings()).isEqualTo(standingB);
            assertThat(changed.kind()).isEqualTo(ZoneKind.QUANTITY);
        }

        @Test
        @DisplayName("자기 이름은 그대로 두고 다른 칸만 고칠 수 있다 — 이름이 겹치는지는 다른 구역 · 블록과만 본다")
        void keepsOwnName() {
            FloorPlan floorPlan = rSeatAndStandingA();

            assertThat(floorPlan.changeZone(R_SEAT_ZONE, ZoneSettings.designated(ZoneName.of("R석"), RED, ZoneBasePrice.of(55_000))))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("재고에 알리는 값 (#54)")
    class Notices {

        @Test
        @DisplayName("구역이 생겼다는 알림은 저장한 구역의 id · 종류 · 정원을 담는다 — 지정형은 정원이 없다")
        void zoneAdded() {
            FloorPlan floorPlan = rSeatAndStandingA();

            assertThat(ZoneAdded.of(EVENT_ID, floorPlan.zone(STANDING_A_ZONE)))
                    .isEqualTo(new ZoneAdded(EVENT_ID, STANDING_A_ZONE, ZoneKind.QUANTITY, ZoneCapacity.of(300)));
            assertThat(ZoneAdded.of(EVENT_ID, floorPlan.zone(R_SEAT_ZONE)))
                    .isEqualTo(new ZoneAdded(EVENT_ID, R_SEAT_ZONE, ZoneKind.DESIGNATED, null));
        }

        @Test
        @DisplayName("정원은 수량형 알림에만 있고, id는 1 이상이다")
        void rejectsInconsistentNotices() {
            assertThatIllegalArgumentException().isThrownBy(() -> new ZoneAdded(EVENT_ID, 3L, ZoneKind.QUANTITY, null));
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ZoneAdded(EVENT_ID, 3L, ZoneKind.DESIGNATED, ZoneCapacity.of(120)));
            assertThatIllegalArgumentException().isThrownBy(() -> new ZoneAdded(EVENT_ID, 0L, ZoneKind.DESIGNATED, null));
            assertThatIllegalArgumentException().isThrownBy(() -> new ZoneCapacityChanged(0L, 3L, ZoneCapacity.of(500)));
            assertThatIllegalArgumentException().isThrownBy(() -> new ZoneRemoved(EVENT_ID, 0L, ZoneKind.QUANTITY));
        }

        @Test
        @DisplayName("저장 전 구역(id 없음)으로는 생겼다는 알림을 만들 수 없다")
        void zoneAddedNeedsSavedZone() {
            FloorPlan floorPlan = FloorPlan.empty(EVENT_ID);
            Zone unsaved = floorPlan.addZone(ZoneKind.DESIGNATED, R_SEAT);

            assertThatExceptionOfType(NoSuchElementException.class)
                    .isThrownBy(() -> ZoneAdded.of(EVENT_ID, unsaved));
        }
    }
}
