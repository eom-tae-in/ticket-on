package com.ticketon.domain.event;

import static com.ticketon.domain.event.EventFixtures.TWELVE_MONTHS_ORGANIZER;
import static com.ticketon.domain.event.EventFixtures.twelveMonthsSpringPlay;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.ticketon.domain.policy.CancellationFeeTier;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("행사 복제 — 작성 중 · \"(복사본)\" 제목, 가져오는 것과 가져오지 않는 것, 도면 · 입구 목록을 새 것으로 통째로 복사 (docs/04 \"행사 복제\", "
        + "docs/02 \"도면은 행사마다 따로 갖습니다\")")
class EventDuplicationTest {

    private static final long ORIGINAL_EVENT = 7L;
    private static final long COPY_EVENT = 8L;
    private static final EventPublicAddress ORIGINAL_ADDRESS = EventPublicAddress.issue("Xb3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0");
    private static final EventPublicAddress COPY_ADDRESS = EventPublicAddress.issue("aB3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG9");
    private static final Instant CREATED_AT = Instant.parse("2025-02-01T01:00:00Z");
    private static final Instant OPENED_AT = Instant.parse("2025-03-01T01:00:00Z");
    private static final Instant NOW = Instant.parse("2026-01-15T01:00:00Z");
    private static final EventPoster ORIGINAL_POSTER = new EventPoster("Pq3kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0.jpg");
    private static final EventPoster COPIED_POSTER = new EventPoster("Cp9kQ9-_mN2pL7vR4tY8wZ1aC5dE6fG0.jpg");

    /** 작년 봄 정기공연의 예매 설정 — 처음 값에서 모두 바꿨다. 오픈 3/1 10:00(한국 시각), 예매 종료 2시간 전, 검표 30분 전, 1회 2석, 1인 6매(행사 전체), 하루 한 회차, 재입장 켬, 만 19세 이상. */
    private static BookingSettings adjustedBookingSettings() {
        return new BookingSettings(OPENED_AT, LeadTime.of(Duration.ofHours(2)), LeadTime.of(Duration.ofMinutes(30)),
                SeatsPerSelectionLimit.of(2), TicketsPerPersonLimit.of(6, TicketsPerPersonBasis.EVENT_TOTAL),
                SessionChoiceRestriction.ONE_SESSION_PER_DATE, ReentrySetting.ALLOWED, AgeRestriction.atLeast(19));
    }

    /** "공연 3일 전까지 전액 환불"(docs/08 "규정은 기준 고정, 관대한 방향만 조정 가능"). */
    private static CancellationPolicy threeDaysFullRefund() {
        return CancellationPolicy.of(List.of(CancellationFeeTier.of(3, 0), CancellationFeeTier.of(1, 30),
                CancellationFeeTier.of(0, 90)));
    }

    /** 극단 열두달의 작년 봄 정기공연 — 그 상태로 저장돼 있다. 암호 보호이고, 포스터를 올렸고, 예매 설정과 취소 규정을 조정했다. */
    private static Event lastSpringPlay(EventStatus status) {
        boolean bookingOpened = status != EventStatus.DRAFT && status != EventStatus.AWAITING_OPEN;
        List<SalesPause> salesPauses = status == EventStatus.SALES_PAUSED
                ? List.of(SalesPause.byOrganizer(1L, null, OPENED_AT.plus(Duration.ofDays(3))))
                : List.of();
        return Event.restore(ORIGINAL_EVENT, TWELVE_MONTHS_ORGANIZER, ORIGINAL_ADDRESS, twelveMonthsSpringPlay(),
                ORIGINAL_POSTER,
                new EventVisibilitySetting(EventVisibility.PASSWORD_PROTECTED, new EventPasswordHash("pbkdf2-hash")),
                adjustedBookingSettings(), threeDaysFullRefund(), status, bookingOpened ? OPENED_AT : null, salesPauses,
                EventWizardStep.ENTRANCES_AND_CHECKIN_ACCOUNTS, CREATED_AT);
    }

    @Nested
    @DisplayName("행사")
    class Duplicate {

        @Test
        @DisplayName("복제본은 같은 주최자의 작성 중 행사이고 제목은 \"○○ (복사본)\"이다 — 새 공개 주소, 진행 단계 1단계, 마지막 수정 시각은 복제한 시각")
        void startsAsDraftCopy() {
            Event copy = lastSpringPlay(EventStatus.ENDED).duplicate(COPY_ADDRESS, COPIED_POSTER, NOW);

            assertThat(copy.id()).isEmpty();
            assertThat(copy.organizerId()).isEqualTo(TWELVE_MONTHS_ORGANIZER);
            assertThat(copy.status()).isEqualTo(EventStatus.DRAFT);
            assertThat(copy.basicInformation().title().value()).isEqualTo("2026 봄 정기공연 〈열두 번째 달〉 (복사본)");
            assertThat(copy.publicAddress()).isEqualTo(COPY_ADDRESS);
            assertThat(copy.wizardProgress()).isEqualTo(EventWizardStep.BASIC_INFORMATION);
            assertThat(copy.wizardProgress().next()).isEqualTo(EventWizardStep.SCHEDULE);
            assertThat(copy.lastModifiedAt()).isEqualTo(NOW);
            assertThat(copy.bookingOpenedAt()).isEmpty();
            assertThat(copy.salesPauses()).isEmpty();
        }

        @Test
        @DisplayName("나머지 기본 정보 — 유형 · 카테고리 · 소개 · 장소 · 상세 안내 · 지도 링크 · 공개용 연락처를 그대로 가져온다")
        void copiesRestOfBasicInformation() {
            EventBasicInformation original = twelveMonthsSpringPlay();

            EventBasicInformation copied = lastSpringPlay(EventStatus.ENDED).duplicate(COPY_ADDRESS, COPIED_POSTER, NOW)
                    .basicInformation();

            assertThat(copied.type()).isEqualTo(original.type());
            assertThat(copied.category()).isEqualTo(original.category());
            assertThat(copied.introduction()).isEqualTo(original.introduction());
            assertThat(copied.venue()).isEqualTo(new Venue("○○아트홀", "서울특별시 종로구 대학로 12"));
            assertThat(copied.venueDetails()).isEqualTo("지하 1층, 주차 불가");
            assertThat(copied.mapLink()).isEqualTo(original.mapLink());
            assertThat(copied.publicContact()).isEqualTo(original.publicContact());
        }

        @Test
        @DisplayName("예매 설정 · 취소 규정 조정은 가져오되 예매 오픈 시각은 비운다 — 오픈 시각은 날짜가 박힌 시각이다")
        void copiesBookingSettingsWithoutOpenTime() {
            Event copy = lastSpringPlay(EventStatus.ENDED).duplicate(COPY_ADDRESS, COPIED_POSTER, NOW);

            BookingSettings settings = copy.bookingSettings();
            assertThat(settings.bookingOpensAt()).isNull();
            assertThat(settings.bookingCloseLeadTime()).isEqualTo(LeadTime.of(Duration.ofHours(2)));
            assertThat(settings.checkinOpenLeadTime()).isEqualTo(LeadTime.of(Duration.ofMinutes(30)));
            assertThat(settings.seatsPerSelection()).isEqualTo(SeatsPerSelectionLimit.of(2));
            assertThat(settings.ticketsPerPerson()).isEqualTo(TicketsPerPersonLimit.of(6, TicketsPerPersonBasis.EVENT_TOTAL));
            assertThat(settings.sessionChoice()).isEqualTo(SessionChoiceRestriction.ONE_SESSION_PER_DATE);
            assertThat(settings.reentry()).isEqualTo(ReentrySetting.ALLOWED);
            assertThat(settings.ageRestriction()).isEqualTo(AgeRestriction.atLeast(19));
            assertThat(settings).isEqualTo(adjustedBookingSettings().withoutBookingOpenTime());
            assertThat(copy.cancellationPolicy()).isEqualTo(threeDaysFullRefund());
        }

        @Test
        @DisplayName("공개 수준은 물려받지 않고 공개로 시작한다 — 암호 보호였어도 암호가 없다")
        void startsPublic() {
            Event copy = lastSpringPlay(EventStatus.ENDED).duplicate(COPY_ADDRESS, COPIED_POSTER, NOW);

            assertThat(copy.visibility()).isEqualTo(EventVisibilitySetting.publicByDefault());
            assertThat(copy.visibility().passwordHash()).isNull();
        }

        @Test
        @DisplayName("포스터는 원본 파일을 새 열쇠로 복사한 것을 붙인다 — 1단계 필수 항목을 갖춰 날짜만 넣으면 판매를 시작할 수 있다")
        void attachesCopiedPoster() {
            Event copy = lastSpringPlay(EventStatus.ENDED).duplicate(COPY_ADDRESS, COPIED_POSTER, NOW);

            assertThat(copy.poster()).contains(COPIED_POSTER);
            assertThat(copy.isBasicInformationComplete()).isTrue();
        }

        @Test
        @DisplayName("원본에 포스터가 없으면 복제본에도 없다")
        void noPosterWithoutOriginalPoster() {
            Event withoutPoster = Event.create(TWELVE_MONTHS_ORGANIZER, ORIGINAL_ADDRESS, twelveMonthsSpringPlay(), CREATED_AT);

            Event copy = withoutPoster.duplicate(COPY_ADDRESS, null, NOW);

            assertThat(copy.poster()).isEmpty();
            assertThat(copy.isBasicInformationComplete()).isFalse();
        }

        @Test
        @DisplayName("원본 포스터의 열쇠를 같이 쓰지 않고, 포스터가 없던 원본의 복제본에 포스터를 만들지 않는다")
        void rejectsSharedOrInventedPoster() {
            Event withoutPoster = Event.create(TWELVE_MONTHS_ORGANIZER, ORIGINAL_ADDRESS, twelveMonthsSpringPlay(), CREATED_AT);

            assertThatIllegalArgumentException()
                    .isThrownBy(() -> lastSpringPlay(EventStatus.ENDED).duplicate(COPY_ADDRESS, ORIGINAL_POSTER, NOW));
            assertThatIllegalArgumentException().isThrownBy(() -> withoutPoster.duplicate(COPY_ADDRESS, COPIED_POSTER, NOW));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(EventStatus.class)
        @DisplayName("상태와 상관없이 복제한다 — 종료된 행사든 진행 중인 행사든 복제본은 작성 중이다")
        void duplicatesInAnyStatus(EventStatus status) {
            Event copy = lastSpringPlay(status).duplicate(COPY_ADDRESS, COPIED_POSTER, NOW);

            assertThat(copy.status()).isEqualTo(EventStatus.DRAFT);
            assertThat(copy.salesPauses()).isEmpty();
            assertThat(copy.bookingOpenedAt()).isEmpty();
        }

        @Test
        @DisplayName("원본은 그대로다 — 제목 · 공개 수준 · 포스터 · 예매 설정 · 상태가 바뀌지 않는다")
        void leavesOriginalUntouched() {
            Event original = lastSpringPlay(EventStatus.SALES_PAUSED);

            original.duplicate(COPY_ADDRESS, COPIED_POSTER, NOW);

            assertThat(original.basicInformation()).isEqualTo(twelveMonthsSpringPlay());
            assertThat(original.visibility().visibility()).isEqualTo(EventVisibility.PASSWORD_PROTECTED);
            assertThat(original.poster()).contains(ORIGINAL_POSTER);
            assertThat(original.bookingSettings()).isEqualTo(adjustedBookingSettings());
            assertThat(original.status()).isEqualTo(EventStatus.SALES_PAUSED);
            assertThat(original.salesPauses()).hasSize(1);
            assertThat(original.lastModifiedAt()).isEqualTo(CREATED_AT);
        }

        @Test
        @DisplayName("복제본을 다시 복제하면 \"(복사본)\"이 한 번 더 붙는다")
        void duplicatesCopy() {
            Event copy = lastSpringPlay(EventStatus.ENDED).duplicate(COPY_ADDRESS, COPIED_POSTER, NOW);

            Event copyOfCopy = copy.duplicate(ORIGINAL_ADDRESS, null, NOW);

            assertThat(copyOfCopy.basicInformation().title().value())
                    .isEqualTo("2026 봄 정기공연 〈열두 번째 달〉 (복사본) (복사본)");
        }

        @Test
        @DisplayName("원본의 공개 주소를 복제본에 주지 않는다")
        void rejectsSamePublicAddress() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> lastSpringPlay(EventStatus.ENDED).duplicate(ORIGINAL_ADDRESS, COPIED_POSTER, NOW));
        }
    }

    @Nested
    @DisplayName("복제본 제목")
    class CopyTitle {

        @Test
        @DisplayName("\"○○공연\"은 \"○○공연 (복사본)\"이다")
        void appendsSuffix() {
            assertThat(EventTitle.of("○○공연").copyTitle().value()).isEqualTo("○○공연 (복사본)");
        }

        @Test
        @DisplayName("94자 제목은 붙여도 100자라 그대로 붙인다")
        void fitsAtLimit() {
            EventTitle copied = EventTitle.of("가".repeat(94)).copyTitle();

            assertThat(copied.value()).isEqualTo("가".repeat(94) + " (복사본)");
            assertThat(copied.value().codePointCount(0, copied.value().length())).isEqualTo(100);
        }

        @Test
        @DisplayName("붙이면 100자를 넘는 제목은 원래 제목의 뒤를 잘라 \"(복사본)\"이 들어갈 자리를 만든다 — 이모지도 한 자로 센다")
        void truncatesLongTitle() {
            assertThat(EventTitle.of("가".repeat(100)).copyTitle().value()).isEqualTo("가".repeat(94) + " (복사본)");
            assertThat(EventTitle.of("가".repeat(93) + "😀" + "나".repeat(6)).copyTitle().value())
                    .isEqualTo("가".repeat(93) + "😀" + " (복사본)");
        }

        @Test
        @DisplayName("자른 끝이 공백이면 걷는다")
        void stripsTrailingSpaceAtCut() {
            assertThat(EventTitle.of("가".repeat(93) + " " + "나".repeat(6)).copyTitle().value())
                    .isEqualTo("가".repeat(93) + " (복사본)");
        }
    }

    @Nested
    @DisplayName("도면")
    class FloorPlanCopy {

        private static final long R_SEAT_ZONE = 1L;
        private static final long S_SEAT_ZONE = 2L;
        private static final long STANDING_A_ZONE = 3L;
        private static final long FIRST_FLOOR = 10L;
        private static final long SECOND_FLOOR = 20L;
        private static final CanvasPoint STAGE = CanvasPoint.of(400, 0);

        private long nextSavedId = 1_001;

        /** 블록 id와 좌석 번호로 정한 시험용 좌석 id — 1층(10) 12열 7번은 11207. */
        private static long seatId(long blockId, int row, int number) {
            return blockId * 1000 + row * 100L + number;
        }

        /** 저장된 블록 — 격자의 모든 자리에 일반 · 활성 좌석이 있고, 앞의 rRows개 행은 R석, 나머지는 S석이다. */
        private static SeatBlock savedBlock(long blockId, String name, int rows, int columns, int rRows, CanvasPoint position,
                BlockAngle angle) {
            List<Seat> seats = new ArrayList<>();
            for (int row = 1; row <= rows; row++) {
                for (int number = 1; number <= columns; number++) {
                    seats.add(Seat.restore(seatId(blockId, row, number), SeatNumber.of(row, number),
                            row <= rRows ? R_SEAT_ZONE : S_SEAT_ZONE, SeatAttribute.GENERAL, true));
                }
            }
            return SeatBlock.restore(blockId, BlockName.of(name), BlockSize.of(rows, columns), position, angle, seats);
        }

        /**
         * 작년 봄 정기공연의 두 층짜리 도면 — 블록 "1층"(12행 × 10열, 1~5열 R석 · 나머지 S석) · "2층"(5행 × 10열, 모두 S석, 15도 돌림), 구역 R석
         * 5만원 · S석 4만원(지정형) · 스탠딩A 3만원 300석(수량형), 무대 위치. 1층 1열 1번은 휠체어석, 1층 12열 1번은 시야제한석, 2층 5열 10번은
         * 기둥이라 꺼 두었다.
         */
        private static FloorPlan lastSpringFloorPlan() {
            FloorPlan plan = FloorPlan.restore(ORIGINAL_EVENT, List.of(
                            Zone.restore(R_SEAT_ZONE, ZoneKind.DESIGNATED, ZoneName.of("R석"), ZoneColor.of("#E5484D"), ZoneBasePrice.of(50_000), null),
                            Zone.restore(S_SEAT_ZONE, ZoneKind.DESIGNATED, ZoneName.of("S석"), ZoneColor.of("#3E63DD"), ZoneBasePrice.of(40_000), null),
                            Zone.restore(STANDING_A_ZONE, ZoneKind.QUANTITY, ZoneName.of("스탠딩A"), ZoneColor.of("#30A46C"),
                                    ZoneBasePrice.of(30_000), ZoneCapacity.of(300))),
                    List.of(savedBlock(FIRST_FLOOR, "1층", 12, 10, 5, CanvasPoint.of(120, 200), BlockAngle.NONE),
                            savedBlock(SECOND_FLOOR, "2층", 5, 10, 0, CanvasPoint.of(120, 600), BlockAngle.ofDegrees(15))),
                    STAGE);
            plan.changeSeatAttribute(Set.of(seatId(FIRST_FLOOR, 1, 1)), SeatAttribute.WHEELCHAIR);
            plan.changeSeatAttribute(Set.of(seatId(FIRST_FLOOR, 12, 1)), SeatAttribute.RESTRICTED_VIEW);
            plan.deactivateSeats(Set.of(seatId(SECOND_FLOOR, 5, 10)));
            return plan;
        }

        /** 저장소가 하듯 저장 전 구역 · 블록 · 좌석에 새 id를 붙인다 — 원본 id(1~20, 10101~)와 겹치지 않게 1001부터. */
        private FloorPlan save(FloorPlan plan) {
            List<Zone> zones = plan.zones().stream()
                    .map(zone -> zone.id().isPresent() ? zone : Zone.restore(nextSavedId++, zone.kind(), zone.name(), zone.color(),
                            zone.basePrice(), zone.capacity().orElse(null)))
                    .toList();
            List<SeatBlock> blocks = plan.blocks().stream()
                    .map(block -> block.id().isPresent() ? block : SeatBlock.restore(nextSavedId++, block.name(), block.size(),
                            block.position(), block.angle(), block.seats().stream()
                                    .map(seat -> Seat.restore(nextSavedId++, seat.number(),
                                            seat.zoneId().isPresent() ? seat.zoneId().getAsLong() : null, seat.attribute(),
                                            seat.isActive()))
                                    .toList()))
                    .toList();
            return FloorPlan.restore(plan.eventId(), zones, blocks, plan.stagePosition().orElse(null));
        }

        /** 원본을 두 단계로 복사하고 저장한 복제본의 도면. */
        private FloorPlan copiedAndSaved(FloorPlan original) {
            FloorPlan copy = save(original.copyZonesFor(COPY_EVENT));
            copy.copyBlocksFrom(original);
            return save(copy);
        }

        private static Seat seatAt(FloorPlan plan, String blockName, int row, int number) {
            return plan.blocks().stream()
                    .filter(block -> block.name().value().equals(blockName))
                    .flatMap(block -> block.seats().stream())
                    .filter(seat -> seat.number().equals(SeatNumber.of(row, number)))
                    .findFirst()
                    .orElseThrow();
        }

        private static String zoneNameOf(FloorPlan plan, Seat seat) {
            return plan.zone(seat.zoneId().getAsLong()).name().value();
        }

        @Test
        @DisplayName("1단계 — 구역(종류 · 이름 · 색 · 기본가 · 정원)과 무대 위치를 새 행사의 도면으로 복사한다. 구역은 새 것이고 블록은 아직 없다")
        void copiesZonesAndStage() {
            FloorPlan copy = lastSpringFloorPlan().copyZonesFor(COPY_EVENT);

            assertThat(copy.eventId()).isEqualTo(COPY_EVENT);
            assertThat(copy.zones()).extracting(zone -> zone.id().isPresent()).containsOnly(false);
            assertThat(copy.zones()).extracting(Zone::settings).containsExactly(
                    ZoneSettings.designated(ZoneName.of("R석"), ZoneColor.of("#E5484D"), ZoneBasePrice.of(50_000)),
                    ZoneSettings.designated(ZoneName.of("S석"), ZoneColor.of("#3E63DD"), ZoneBasePrice.of(40_000)),
                    ZoneSettings.quantity(ZoneName.of("스탠딩A"), ZoneColor.of("#30A46C"), ZoneBasePrice.of(30_000),
                            ZoneCapacity.of(300)));
            assertThat(copy.zones()).extracting(Zone::kind)
                    .containsExactly(ZoneKind.DESIGNATED, ZoneKind.DESIGNATED, ZoneKind.QUANTITY);
            assertThat(copy.blocks()).isEmpty();
            assertThat(copy.stagePosition()).contains(STAGE);
        }

        @Test
        @DisplayName("2단계 — 저장한 복제본 도면에 블록(이름 · 크기 · 위치 · 각도)과 좌석(번호 · 속성 · 활성)을 얹고, 좌석은 같은 이름의 복제본 구역을 가리킨다")
        void copiesBlocksAndSeats() {
            FloorPlan original = lastSpringFloorPlan();
            FloorPlan copy = save(original.copyZonesFor(COPY_EVENT));

            copy.copyBlocksFrom(original);

            assertThat(copy.blocks()).extracting(block -> block.id().isPresent()).containsOnly(false);
            assertThat(copy.blocks()).extracting(block -> block.name().value()).containsExactly("1층", "2층");
            assertThat(copy.blocks()).extracting(SeatBlock::size).containsExactly(BlockSize.of(12, 10), BlockSize.of(5, 10));
            assertThat(copy.blocks()).extracting(SeatBlock::position).containsExactly(CanvasPoint.of(120, 200), CanvasPoint.of(120, 600));
            assertThat(copy.blocks()).extracting(SeatBlock::angle).containsExactly(BlockAngle.NONE, BlockAngle.ofDegrees(15));
            assertThat(copy.blocks()).flatExtracting(SeatBlock::seats).hasSize(170)
                    .extracting(seat -> seat.id().isPresent()).containsOnly(false);
            assertThat(seatAt(copy, "1층", 1, 1).attribute()).isEqualTo(SeatAttribute.WHEELCHAIR);
            assertThat(seatAt(copy, "1층", 12, 1).attribute()).isEqualTo(SeatAttribute.RESTRICTED_VIEW);
            assertThat(seatAt(copy, "2층", 5, 10).isActive()).isFalse();
            assertThat(seatAt(copy, "2층", 5, 9).isActive()).isTrue();
            assertThat(zoneNameOf(copy, seatAt(copy, "1층", 5, 7))).isEqualTo("R석");
            assertThat(zoneNameOf(copy, seatAt(copy, "1층", 12, 7))).isEqualTo("S석");
            assertThat(copy.zones()).extracting(zone -> zone.id().getAsLong())
                    .doesNotContain(R_SEAT_ZONE, S_SEAT_ZONE, STANDING_A_ZONE);
        }

        @Test
        @DisplayName("저장한 복제본은 원본과 id를 하나도 같이 쓰지 않는다 — 구역 · 블록 · 좌석 모두 새 id다")
        void savedCopySharesNoIds() {
            FloorPlan original = lastSpringFloorPlan();

            FloorPlan copy = copiedAndSaved(original);

            assertThat(copy.zones()).extracting(zone -> zone.id().getAsLong())
                    .doesNotContainAnyElementsOf(original.zones().stream().map(zone -> zone.id().getAsLong()).toList());
            assertThat(copy.blocks()).extracting(block -> block.id().getAsLong())
                    .doesNotContainAnyElementsOf(original.blocks().stream().map(block -> block.id().getAsLong()).toList());
            List<Long> originalSeatIds = original.blocks().stream().flatMap(block -> block.seats().stream())
                    .map(seat -> seat.id().getAsLong()).toList();
            assertThat(copy.blocks()).flatExtracting(SeatBlock::seats).extracting(seat -> seat.id().getAsLong())
                    .doesNotContainAnyElementsOf(originalSeatIds);
            assertThat(copy.labelOf(seatAt(copy, "1층", 12, 7).id().getAsLong()).value()).isEqualTo("1층 12열 7번");
        }

        @Test
        @DisplayName("올해 도면에서 \"1층\"을 \"지하 1층\"으로 바꾸고 12열을 없애도 작년 도면에는 여전히 \"1층 12열 7번\"이 있다 (docs/02)")
        void editingCopyLeavesOriginal() {
            FloorPlan original = lastSpringFloorPlan();
            FloorPlan copy = copiedAndSaved(original);
            long copiedFirstFloor = copy.blocks().getFirst().id().getAsLong();
            Set<Long> copiedRowTwelve = new HashSet<>();
            for (int number = 1; number <= 10; number++) {
                copiedRowTwelve.add(seatAt(copy, "1층", 12, number).id().getAsLong());
            }

            copy.renameBlock(copiedFirstFloor, BlockName.of("지하 1층"));
            copy.deactivateSeats(copiedRowTwelve);
            copy.changeZoneBasePrice(copy.zones().getFirst().id().getAsLong(), ZoneBasePrice.of(60_000));

            long originalSeat = seatId(FIRST_FLOOR, 12, 7);
            assertThat(original.labelOf(originalSeat).value()).isEqualTo("1층 12열 7번");
            assertThat(original.seat(originalSeat).isActive()).isTrue();
            assertThat(original.zone(R_SEAT_ZONE).basePrice()).isEqualTo(ZoneBasePrice.of(50_000));
            assertThat(copy.labelOf(seatAt(copy, "지하 1층", 12, 7).id().getAsLong()).value()).isEqualTo("지하 1층 12열 7번");
        }

        @Test
        @DisplayName("빈 도면을 복제하면 빈 도면이다 — 무대 위치도 정하지 않은 채다")
        void copiesEmptyFloorPlan() {
            FloorPlan empty = FloorPlan.empty(ORIGINAL_EVENT);

            FloorPlan copy = save(empty.copyZonesFor(COPY_EVENT));
            copy.copyBlocksFrom(empty);

            assertThat(copy.zones()).isEmpty();
            assertThat(copy.blocks()).isEmpty();
            assertThat(copy.stagePosition()).isEmpty();
        }

        @Test
        @DisplayName("원본 행사의 id로는 복사하지 않고, 저장하기 전이나 블록이 이미 있는 도면에는 블록을 얹지 않는다")
        void rejectsMisuse() {
            FloorPlan original = lastSpringFloorPlan();

            assertThatIllegalArgumentException().isThrownBy(() -> original.copyZonesFor(ORIGINAL_EVENT));
            assertThatIllegalStateException().isThrownBy(() -> original.copyZonesFor(COPY_EVENT).copyBlocksFrom(original));
            assertThatIllegalStateException().isThrownBy(() -> copiedAndSaved(original).copyBlocksFrom(original));
        }
    }

    @Nested
    @DisplayName("일정 — 회차 방식 · 기본 운영 시간만, 날짜 · 회차는 빼고")
    class ScheduleCopy {

        /** 작년 봄 정기공연의 회차형 일정 — 3/14 15:00 · 19:00, 3/15 19:00. */
        private static EventSchedule timedSchedule() {
            EventSchedule schedule = EventSchedule.timed(ORIGINAL_EVENT);
            schedule.addSession(LocalDate.of(2025, 3, 14), TimedSessionTime.of(LocalTime.of(15, 0), LocalTime.of(17, 0)));
            schedule.addSession(LocalDate.of(2025, 3, 14), TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0)));
            schedule.addSession(LocalDate.of(2025, 3, 15), TimedSessionTime.startingAt(LocalTime.of(19, 0)));
            return schedule;
        }

        @Test
        @DisplayName("회차형은 회차 방식만 가져오고 날짜 · 회차는 가져오지 않는다 — 원본의 회차는 그대로다")
        void copiesTimedModeWithoutDates() {
            EventSchedule original = timedSchedule();

            EventSchedule copy = original.copyModeFor(COPY_EVENT);

            assertThat(copy.eventId()).isEqualTo(COPY_EVENT);
            assertThat(copy.mode()).isEqualTo(SessionMode.TIMED);
            assertThat(copy.defaultOperatingHours()).isEmpty();
            assertThat(copy.dates()).isEmpty();
            assertThat(original.sessions()).hasSize(3);
        }

        @Test
        @DisplayName("종일형은 기본 운영 시간(10:00~18:00, 마지막 입장 17:00)까지 가져오고, 주말만 늦게까지 덮어쓴 날짜별 운영 시간은 날짜와 함께 빠진다")
        void copiesAllDayModeAndDefaultOperatingHours() {
            OperatingHours weekdays = OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(18, 0), LocalTime.of(17, 0));
            EventSchedule original = EventSchedule.allDay(ORIGINAL_EVENT, weekdays);
            original.addDate(LocalDate.of(2025, 3, 14));
            original.addDate(LocalDate.of(2025, 3, 15));
            original.overrideOperatingHours(LocalDate.of(2025, 3, 15), OperatingHours.of(LocalTime.of(10, 0), LocalTime.of(21, 0)));

            EventSchedule copy = original.copyModeFor(COPY_EVENT);

            assertThat(copy.mode()).isEqualTo(SessionMode.ALL_DAY);
            assertThat(copy.defaultOperatingHours()).contains(weekdays);
            assertThat(copy.dates()).isEmpty();
            assertThat(original.dates()).hasSize(2);
        }

        @Test
        @DisplayName("복사한 일정은 회차 방식을 처음 고른 직후와 같다 — 새 날짜 · 회차를 더할 수 있다")
        void copiedScheduleTakesNewSessions() {
            EventSchedule copy = timedSchedule().copyModeFor(COPY_EVENT);

            copy.addSession(LocalDate.of(2026, 3, 13), TimedSessionTime.of(LocalTime.of(19, 0), LocalTime.of(21, 0)));

            assertThat(copy.sessions()).hasSize(1);
            assertThat(copy.sessions()).extracting(session -> session.id().isPresent()).containsOnly(false);
        }

        @Test
        @DisplayName("원본 행사의 id로는 복사하지 않는다")
        void rejectsSameEvent() {
            assertThatIllegalArgumentException().isThrownBy(() -> timedSchedule().copyModeFor(ORIGINAL_EVENT));
        }
    }

    @Nested
    @DisplayName("입구 목록")
    class EntrancesCopy {

        private static EventEntrances lastSpringEntrances() {
            return EventEntrances.restore(ORIGINAL_EVENT, List.of(Entrance.restore(31L, EntranceName.of("정문")),
                    Entrance.restore(32L, EntranceName.of("2층 전용")), Entrance.restore(33L, EntranceName.of("VIP 입구"))));
        }

        @Test
        @DisplayName("입구를 이름 · 순서 그대로 새 입구로 복사한다 — 원본 목록은 그대로다")
        void copiesEntrances() {
            EventEntrances original = lastSpringEntrances();

            EventEntrances copy = original.copyFor(COPY_EVENT);

            assertThat(copy.eventId()).isEqualTo(COPY_EVENT);
            assertThat(copy.entrances()).extracting(entrance -> entrance.name().value()).containsExactly("정문", "2층 전용", "VIP 입구");
            assertThat(copy.entrances()).extracting(entrance -> entrance.id().isPresent()).containsOnly(false);
            copy.add(EntranceName.of("옆문"));
            assertThat(original.entrances()).hasSize(3);
        }

        @Test
        @DisplayName("원본 행사의 id로는 복사하지 않는다")
        void rejectsSameEvent() {
            assertThatIllegalArgumentException().isThrownBy(() -> lastSpringEntrances().copyFor(ORIGINAL_EVENT));
        }
    }
}
