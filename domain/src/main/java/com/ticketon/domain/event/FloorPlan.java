package com.ticketon.domain.event;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 도면 — 극장이 어떻게 생겼는가(docs/02 "두 개의 축", docs/14 "1층 — 도면"). 구역({@link Zone}), 블록({@link SeatBlock})과 그 안의 좌석
 * ({@link Seat}), 무대 위치를 갖는다. 위저드 3단계에서 만든다(docs/03 "행사 만들기 위저드" — 블록 · 구역 · 좌석 배치도 또는 정원).
 * <pre>
 * 【도면】 회차와 무관. 행사에 하나
 *   블록 ─ 좌석     ← 번호를 매김
 *   구역            ← 가격을 매김
 *     └─ 좌석을 구역에 배정
 * </pre>
 * <b>도면은 행사에 하나이고 회차마다 다를 수 없다</b>(docs/99 B "회차별 도면"). 회차마다 도면이 생기면 구역 기본가도 회차 수만큼 생겨 "기본가는
 * 하나, 회차에는 조정값만"(docs/05)이 성립하지 않는다. 판매 상태 · 판매 제외처럼 회차마다 다른 것은 재고가 회차 × 좌석으로 갖는다(#53). 다른 행사와
 * 도면을 같이 쓰지 않는다 — 행사를 복제하면 통째로 복사되고 그 뒤로 아무 관계가 없다(docs/02 "도면은 행사마다 따로 갖습니다").
 * <ul>
 *   <li><b>구역</b> — 지정형은 좌석을 배정받고, 수량형은 정원만 있다. <b>수량형 구역에는 블록도 좌석도 없다</b> — 좌석을 배정하려 하면
 *       {@link EventErrorCode#QUANTITY_ZONE_HAS_NO_SEATS}로 막는다. 구역을 지우면 그 구역에 배정했던 좌석은 배정이 풀린다.</li>
 *   <li><b>블록</b> — 이름 + 행 × 열 덩어리를 얹으면 격자의 모든 자리에 좌석이 생긴다. 위치를 옮기고 각도를 돌린다(부채꼴 객석).</li>
 *   <li><b>이름 규칙</b> — 블록 이름은 자유 입력이지만 같은 이름의 블록 둘을 만들 수 없고({@link EventErrorCode#BLOCK_NAME_ALREADY_TAKEN}) 구역
 *       이름과도 겹칠 수 없다({@link EventErrorCode#BLOCK_NAME_TAKEN_BY_ZONE} — docs/02 "'R석'은 이미 구역 이름으로 쓰고 있습니다"). 구역 이름도
 *       블록 이름과 겹칠 수 없다({@link EventErrorCode#ZONE_NAME_TAKEN_BY_BLOCK}, #42). 확인 필요 (#37) — 구역 이름끼리도 겹칠 수 없다
 *       ({@link EventErrorCode#ZONE_NAME_ALREADY_TAKEN}): 스탠딩 구역이 여럿이면 구역 이름으로 나누고(docs/02 "설정 흐름") 수량형 티켓 표기가
 *       구역 이름으로 구역을 가리키므로("스탠딩A · 0091-2") 같은 이름의 구역 둘은 관객이 가를 수 없다. 이름은 앞뒤 공백을 걷고 NFC로 맞춘 값끼리
 *       글자 그대로 견준다.</li>
 *   <li><b>자동 이름</b> — 블록 이름을 비우면 "블록 1", "블록 2"를 붙인다(docs/02 "블록"). 확인 필요 (#37) — 번호는 다른 블록 · 구역이 쓰고 있지
 *       않은 가장 작은 수다. 이름을 모두 비우고 블록 셋을 얹으면 "블록 1" · "블록 2" · "블록 3"이 되고, 직접 "블록 2"라고 지은 블록이 있으면 그
 *       번호는 건너뛴다.</li>
 *   <li><b>좌석</b> — 여러 좌석을 한 번에 구역에 배정(색칠)하거나 배정을 풀고, 속성(일반 · 휠체어 · 시야제한)을 정하고, 비활성화한다. 200석짜리
 *       소극장이 5분 안에 만들어져야 해서(docs/02 "에디터는 블록 단위") 좌석을 하나씩이 아니라 묶음으로 다룬다. 묶음 안에 도면에 없는 좌석이 하나라도
 *       있으면 아무것도 바꾸지 않는다.</li>
 *   <li><b>무대 위치</b> — 배치도 위의 한 점. 정하기 전에는 비어 있다.</li>
 *   <li><b>좌석 표기</b> — 블록이 하나면 "12열 7번", 여러 개면 "1층 12열 7번"({@link #labelOf}, {@link SeatLabel}).</li>
 * </ul>
 * <b>좌석의 정체성은 좌석 id다</b>({@link Seat}). 재고 · 티켓 · 검표가 좌석 id를 가리키고, 도면의 어떤 수정도 좌석의 id와 번호를 바꾸지 않는다 —
 * 블록 이름을 고치면 표기만 따라 바뀌고(티켓은 하나도 손대지 않는다, docs/07 "티켓이 직접 갖는 것과 가리키기만 하는 것"), 블록을 옮기거나 돌려도,
 * 다른 좌석을 비활성화해도 번호는 그대로다.
 * <p>
 * 언제 무엇을 고칠 수 있는지는 아직 보지 않는다 — 판매 시작 뒤 티켓(예매 · 초대권, 취소 · 회수되지 않은 것)이 걸린 좌석은 옮기거나 지울 수 없고
 * 티켓이 있는 구역은 지울 수 없다(docs/04 "예매가 열린 뒤(판매 중) 수정 범위", #64). 판매가 끝나면 도면은 고정된다
 * ({@link EventEditTarget#FLOOR_PLAN}, {@link Event#requireEditable}). 팔린 좌석이 있는 블록의
 * 이름을 바꾸면 "예매자에게 알릴까요?" 창을 띄운다(#110). 이 확인은 부르는 쪽이 한다. 지운 블록 · 좌석 · 구역을 기록으로 남기는 일은 저장소가 한다
 * (#42, #43 — 취소 · 회수된 티켓은 원래 좌석을 그대로 가리킨다).
 * <p>
 * 입구 목록은 같은 도면에 속하지만 판매가 끝난 뒤에도 고칠 수 있어 따로 둔다({@link EventEntrances}).
 * <p>
 * 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 구역 · 블록 · 좌석을 id로 고치는 일은 저장한 뒤에 한다.
 */
public final class FloorPlan {

    private final long eventId;
    private final List<Zone> zones;
    private final List<SeatBlock> blocks;
    private CanvasPoint stagePosition;

    private FloorPlan(long eventId, List<Zone> zones, List<SeatBlock> blocks, CanvasPoint stagePosition) {
        if (eventId < 1) {
            throw new IllegalArgumentException("행사 id는 1 이상이어야 합니다: " + eventId);
        }
        this.eventId = eventId;
        this.zones = new ArrayList<>(zones);
        this.blocks = new ArrayList<>(blocks);
        this.stagePosition = stagePosition;
        requireConsistent();
    }

    /** 빈 도면 — 구역 · 블록이 없고 무대 위치를 정하지 않았다(템플릿의 "빈 캔버스", docs/02 "템플릿 우선"). */
    public static FloorPlan empty(long eventId) {
        return new FloorPlan(eventId, List.of(), List.of(), null);
    }

    /**
     * 저장소에서 읽은 값으로 도면을 되살린다. 구역 · 블록 · 좌석은 모두 저장된 것(id가 있는 것)이어야 한다.
     *
     * @param stagePosition 무대 위치. 정하지 않았으면 null
     * @throws IllegalArgumentException id가 없거나 겹치면, 이름이 겹치면, 좌석이 도면에 없는 구역이나 수량형 구역에 배정돼 있으면
     */
    public static FloorPlan restore(long eventId, List<Zone> zones, List<SeatBlock> blocks, CanvasPoint stagePosition) {
        Objects.requireNonNull(zones, "zones");
        Objects.requireNonNull(blocks, "blocks");
        zones.forEach(zone -> requireSaved(zone.id().isPresent(), "구역"));
        blocks.forEach(block -> {
            requireSaved(block.id().isPresent(), "블록");
            block.seats().forEach(seat -> requireSaved(seat.id().isPresent(), "좌석"));
        });
        return new FloorPlan(eventId, zones, blocks, stagePosition);
    }

    /**
     * 지정형 구역을 더한다. 아직 배정된 좌석이 없다.
     *
     * @return 더한 구역(아직 id 없음)
     * @throws RuleViolationException 같은 이름의 구역이 있거나({@link EventErrorCode#ZONE_NAME_ALREADY_TAKEN}), 블록 이름으로 쓰고 있으면
     *                                ({@link EventErrorCode#ZONE_NAME_TAKEN_BY_BLOCK})
     */
    public Zone addDesignatedZone(ZoneName name, ZoneColor color, ZoneBasePrice basePrice) {
        requireZoneNameAvailable(name, null);
        Zone zone = Zone.newDesignated(name, color, basePrice);
        zones.add(zone);
        return zone;
    }

    /**
     * 수량형 구역을 더한다 — 정원 입력만으로 끝난다.
     *
     * @return 더한 구역(아직 id 없음)
     * @throws RuleViolationException 같은 이름의 구역이 있거나({@link EventErrorCode#ZONE_NAME_ALREADY_TAKEN}), 블록 이름으로 쓰고 있으면
     *                                ({@link EventErrorCode#ZONE_NAME_TAKEN_BY_BLOCK})
     */
    public Zone addQuantityZone(ZoneName name, ZoneColor color, ZoneBasePrice basePrice, ZoneCapacity capacity) {
        requireZoneNameAvailable(name, null);
        Zone zone = Zone.newQuantity(name, color, basePrice, capacity);
        zones.add(zone);
        return zone;
    }

    /**
     * 구역 이름을 바꾼다. 수량형 티켓 표기("스탠딩A · 0091-2")도 따라 바뀐다 — 티켓은 구역을 가리키기만 한다(docs/07).
     *
     * @return 바뀌었는지 — 지금과 같으면 false
     * @throws NotFoundException      도면에 없는 구역이면({@link EventErrorCode#ZONE_NOT_FOUND})
     * @throws RuleViolationException 다른 구역이 쓰는 이름이거나({@link EventErrorCode#ZONE_NAME_ALREADY_TAKEN}), 블록 이름으로 쓰고 있으면
     *                                ({@link EventErrorCode#ZONE_NAME_TAKEN_BY_BLOCK})
     */
    public boolean renameZone(long zoneId, ZoneName newName) {
        Objects.requireNonNull(newName, "newName");
        Zone zone = zone(zoneId);
        requireZoneNameAvailable(newName, zone);
        return zone.rename(newName);
    }

    /**
     * 구역 색을 바꾼다.
     *
     * @return 바뀌었는지 — 지금과 같으면 false
     * @throws NotFoundException 도면에 없는 구역이면({@link EventErrorCode#ZONE_NOT_FOUND})
     */
    public boolean changeZoneColor(long zoneId, ZoneColor newColor) {
        return zone(zoneId).changeColor(newColor);
    }

    /**
     * 구역 기본가를 바꾼다. 판매 중이면 신규 예매부터 적용된다 — 이미 산 표는 결제 금액을 직접 가진다(docs/04, docs/07).
     *
     * @return 바뀌었는지 — 지금과 같으면 false
     * @throws NotFoundException 도면에 없는 구역이면({@link EventErrorCode#ZONE_NOT_FOUND})
     */
    public boolean changeZoneBasePrice(long zoneId, ZoneBasePrice newBasePrice) {
        return zone(zoneId).changeBasePrice(newBasePrice);
    }

    /**
     * 수량형 구역의 정원을 바꾼다. 판매를 시작한 뒤 판매 수량 아래로 줄이지 못하게 하는 일은 재고가 한다(#53, #64).
     *
     * @return 바뀌었는지 — 지금과 같으면 false
     * @throws NotFoundException     도면에 없는 구역이면({@link EventErrorCode#ZONE_NOT_FOUND})
     * @throws IllegalStateException 지정형 구역이면 — 지정형의 자리는 배정한 좌석이다. 부르는 쪽이 구역 종류를 보고 부른다
     */
    public boolean changeZoneCapacity(long zoneId, ZoneCapacity newCapacity) {
        return zone(zoneId).changeCapacity(newCapacity);
    }

    /**
     * 구역을 지운다. 그 구역에 배정했던 좌석은 배정이 풀린다 — 비활성 좌석도 같다. 티켓이 있는 구역을 지울 수 있는지는 부르는 쪽이 본다(#64).
     *
     * @throws NotFoundException 도면에 없는 구역이면({@link EventErrorCode#ZONE_NOT_FOUND})
     */
    public void removeZone(long zoneId) {
        Zone zone = zone(zoneId);
        allSeats().stream().filter(seat -> seat.isIn(zoneId)).forEach(Seat::unassign);
        zones.remove(zone);
    }

    /**
     * 블록을 얹는다 — 행 × 열 격자의 모든 자리에 활성 · 일반 · 구역 없는 좌석이 생긴다. 이름을 비웠으면 "블록 N"을 붙인다.
     *
     * @param requestedName 블록 이름. 비웠으면 null — 다른 블록 · 구역이 쓰지 않는 가장 작은 N으로 "블록 N"을 붙인다
     * @return 얹은 블록(블록 · 좌석 모두 아직 id 없음)
     * @throws RuleViolationException 같은 이름의 블록이 있거나({@link EventErrorCode#BLOCK_NAME_ALREADY_TAKEN}), 구역 이름으로 쓰고 있으면
     *                                ({@link EventErrorCode#BLOCK_NAME_TAKEN_BY_ZONE})
     */
    public SeatBlock addBlock(BlockName requestedName, BlockSize size, CanvasPoint position, BlockAngle angle) {
        BlockName name = resolveBlockName(requestedName, null);
        SeatBlock block = SeatBlock.newBlock(name, size, position, angle);
        blocks.add(block);
        return block;
    }

    /**
     * 블록 이름을 바꾼다. 이미 팔린 표의 표기도 따라 바뀐다 — 이름은 블록 한 곳에만 있고 티켓은 좌석을 가리키기만 한다. 이력은 남기지 않는다
     * (docs/02 "판매 중에도 블록 이름을 고칠 수 있습니다"). 좌석의 id와 번호는 그대로다.
     *
     * @param requestedName 새 이름. 비웠으면 null — 블록을 얹을 때처럼 "블록 N"을 붙인다
     * @return 바뀌었는지 — 지금과 같으면 false. 바뀌었고 팔린 좌석이 있으면 "예매자에게 알릴까요?" 창을 띄운다(#110)
     * @throws NotFoundException      도면에 없는 블록이면({@link EventErrorCode#BLOCK_NOT_FOUND})
     * @throws RuleViolationException 다른 블록이 쓰는 이름이거나({@link EventErrorCode#BLOCK_NAME_ALREADY_TAKEN}), 구역 이름으로 쓰고 있으면
     *                                ({@link EventErrorCode#BLOCK_NAME_TAKEN_BY_ZONE})
     */
    public boolean renameBlock(long blockId, BlockName requestedName) {
        SeatBlock block = block(blockId);
        return block.rename(resolveBlockName(requestedName, block));
    }

    /**
     * 블록을 옮긴다. 좌석은 블록 안의 자리 · 번호 그대로 함께 옮겨진다.
     *
     * @return 바뀌었는지 — 지금과 같으면 false
     * @throws NotFoundException 도면에 없는 블록이면({@link EventErrorCode#BLOCK_NOT_FOUND})
     */
    public boolean moveBlock(long blockId, CanvasPoint newPosition) {
        return block(blockId).moveTo(newPosition);
    }

    /**
     * 블록을 돌린다(부채꼴 객석). 좌석은 블록 안의 자리 · 번호 그대로 함께 돌아간다.
     *
     * @return 바뀌었는지 — 지금과 같으면 false
     * @throws NotFoundException 도면에 없는 블록이면({@link EventErrorCode#BLOCK_NOT_FOUND})
     */
    public boolean rotateBlock(long blockId, BlockAngle newAngle) {
        return block(blockId).rotateTo(newAngle);
    }

    /**
     * 블록을 그 안의 좌석과 함께 지운다. 티켓이 걸린 좌석이 있는 블록을 지울 수 있는지는 부르는 쪽이 본다(#64).
     *
     * @return 지운 블록 — 좌석이 함께 빠졌음을 재고에 알리는 데 쓴다(#43)
     * @throws NotFoundException 도면에 없는 블록이면({@link EventErrorCode#BLOCK_NOT_FOUND})
     */
    public SeatBlock removeBlock(long blockId) {
        SeatBlock block = block(blockId);
        blocks.remove(block);
        return block;
    }

    /**
     * 좌석들을 지정형 구역에 배정한다(색칠). 다른 구역에 배정돼 있던 좌석은 이 구역으로 옮겨진다.
     *
     * @return 하나라도 바뀌었는지
     * @throws NotFoundException      도면에 없는 구역이거나({@link EventErrorCode#ZONE_NOT_FOUND}) 좌석이면({@link EventErrorCode#SEAT_NOT_FOUND})
     * @throws RuleViolationException 수량형 구역이면({@link EventErrorCode#QUANTITY_ZONE_HAS_NO_SEATS})
     */
    public boolean assignSeatsToZone(Set<Long> seatIds, long zoneId) {
        Zone zone = zone(zoneId);
        if (!zone.takesSeats()) {
            throw new RuleViolationException(EventErrorCode.QUANTITY_ZONE_HAS_NO_SEATS, "행사 " + eventId + ", 구역 " + zoneId);
        }
        return changeEach(requireSeats(seatIds), seat -> seat.assignTo(zoneId));
    }

    /**
     * 좌석들의 구역 배정을 푼다(색 지우기).
     *
     * @return 하나라도 바뀌었는지
     * @throws NotFoundException 도면에 없는 좌석이면({@link EventErrorCode#SEAT_NOT_FOUND})
     */
    public boolean unassignSeats(Set<Long> seatIds) {
        return changeEach(requireSeats(seatIds), Seat::unassign);
    }

    /**
     * 좌석들의 속성을 정한다 — 일반 · 휠체어 · 시야제한.
     *
     * @return 하나라도 바뀌었는지
     * @throws NotFoundException 도면에 없는 좌석이면({@link EventErrorCode#SEAT_NOT_FOUND})
     */
    public boolean changeSeatAttribute(Set<Long> seatIds, SeatAttribute attribute) {
        Objects.requireNonNull(attribute, "attribute");
        return changeEach(requireSeats(seatIds), seat -> seat.changeAttribute(attribute));
    }

    /**
     * 좌석들을 비활성화한다 — 통로 · 기둥 같은 빈 자리로 둔다. 번호는 다시 매기지 않고, 구역 · 속성은 그대로 둔다. 티켓이 걸린 좌석을 끌 수
     * 있는지는 부르는 쪽이 본다(#64).
     *
     * @return 하나라도 바뀌었는지
     * @throws NotFoundException 도면에 없는 좌석이면({@link EventErrorCode#SEAT_NOT_FOUND})
     */
    public boolean deactivateSeats(Set<Long> seatIds) {
        return changeEach(requireSeats(seatIds), seat -> seat.changeActive(false));
    }

    /**
     * 비활성 좌석들을 다시 켠다 — 같은 좌석이 같은 번호로 돌아온다.
     *
     * @return 하나라도 바뀌었는지
     * @throws NotFoundException 도면에 없는 좌석이면({@link EventErrorCode#SEAT_NOT_FOUND})
     */
    public boolean activateSeats(Set<Long> seatIds) {
        return changeEach(requireSeats(seatIds), seat -> seat.changeActive(true));
    }

    /**
     * 무대 위치를 정한다.
     *
     * @return 바뀌었는지 — 지금과 같으면 false
     */
    public boolean placeStage(CanvasPoint position) {
        Objects.requireNonNull(position, "position");
        if (position.equals(stagePosition)) {
            return false;
        }
        stagePosition = position;
        return true;
    }

    /**
     * 좌석 표기 — 블록이 하나면 "12열 7번", 여러 개면 "1층 12열 7번". 지금의 블록 이름 · 블록 수로 만든다.
     *
     * @throws NotFoundException 도면에 없는 좌석이면({@link EventErrorCode#SEAT_NOT_FOUND})
     */
    public SeatLabel labelOf(long seatId) {
        Seat seat = seat(seatId);
        SeatBlock block = blocks.stream().filter(candidate -> candidate.contains(seat)).findFirst().orElseThrow();
        return SeatLabel.of(block.name(), seat.number(), blocks.size());
    }

    /**
     * 그 구역에 배정된 활성 좌석 — 블록 순서, 같은 블록은 행 · 번호 순서. 수량형 구역은 늘 비어 있다.
     *
     * @throws NotFoundException 도면에 없는 구역이면({@link EventErrorCode#ZONE_NOT_FOUND})
     */
    public List<Seat> seatsIn(long zoneId) {
        zone(zoneId);
        return allSeats().stream().filter(Seat::isActive).filter(seat -> seat.isIn(zoneId)).toList();
    }

    /**
     * 그 구역.
     *
     * @throws NotFoundException 도면에 없는 구역이면({@link EventErrorCode#ZONE_NOT_FOUND})
     */
    public Zone zone(long zoneId) {
        return zones.stream()
                .filter(zone -> zone.hasId(zoneId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(EventErrorCode.ZONE_NOT_FOUND, "행사 " + eventId + ", 구역 " + zoneId));
    }

    /**
     * 그 블록.
     *
     * @throws NotFoundException 도면에 없는 블록이면({@link EventErrorCode#BLOCK_NOT_FOUND})
     */
    public SeatBlock block(long blockId) {
        return blocks.stream()
                .filter(block -> block.hasId(blockId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(EventErrorCode.BLOCK_NOT_FOUND, "행사 " + eventId + ", 블록 " + blockId));
    }

    /**
     * 그 좌석.
     *
     * @throws NotFoundException 도면에 없는 좌석이면({@link EventErrorCode#SEAT_NOT_FOUND})
     */
    public Seat seat(long seatId) {
        return blocks.stream()
                .flatMap(block -> block.findSeat(seatId).stream())
                .findFirst()
                .orElseThrow(() -> new NotFoundException(EventErrorCode.SEAT_NOT_FOUND, "행사 " + eventId + ", 좌석 " + seatId));
    }

    public long eventId() {
        return eventId;
    }

    /** 구역 — 더한 순서. */
    public List<Zone> zones() {
        return Collections.unmodifiableList(zones);
    }

    /** 블록 — 얹은 순서. */
    public List<SeatBlock> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    /** 무대 위치. 정하지 않았으면 비어 있다. */
    public Optional<CanvasPoint> stagePosition() {
        return Optional.ofNullable(stagePosition);
    }

    private BlockName resolveBlockName(BlockName requestedName, SeatBlock excluded) {
        if (requestedName == null) {
            return automaticBlockName(excluded);
        }
        requireBlockNameAvailable(requestedName, excluded);
        return requestedName;
    }

    private BlockName automaticBlockName(SeatBlock excluded) {
        for (int sequence = 1; ; sequence++) {
            BlockName candidate = BlockName.automatic(sequence);
            if (!isTakenByOtherBlock(candidate.value(), excluded) && !isTakenByOtherZone(candidate.value(), null)) {
                return candidate;
            }
        }
    }

    private void requireBlockNameAvailable(BlockName name, SeatBlock excluded) {
        if (isTakenByOtherBlock(name.value(), excluded)) {
            throw new RuleViolationException(EventErrorCode.BLOCK_NAME_ALREADY_TAKEN, "행사 " + eventId + ", 블록 이름 " + name.value());
        }
        if (isTakenByOtherZone(name.value(), null)) {
            throw new RuleViolationException(EventErrorCode.BLOCK_NAME_TAKEN_BY_ZONE, "행사 " + eventId + ", 블록 이름 " + name.value());
        }
    }

    private void requireZoneNameAvailable(ZoneName name, Zone excluded) {
        Objects.requireNonNull(name, "name");
        if (isTakenByOtherZone(name.value(), excluded)) {
            throw new RuleViolationException(EventErrorCode.ZONE_NAME_ALREADY_TAKEN, "행사 " + eventId + ", 구역 이름 " + name.value());
        }
        if (isTakenByOtherBlock(name.value(), null)) {
            throw new RuleViolationException(EventErrorCode.ZONE_NAME_TAKEN_BY_BLOCK, "행사 " + eventId + ", 구역 이름 " + name.value());
        }
    }

    private boolean isTakenByOtherBlock(String name, SeatBlock excluded) {
        return blocks.stream().filter(block -> block != excluded).anyMatch(block -> block.name().value().equals(name));
    }

    private boolean isTakenByOtherZone(String name, Zone excluded) {
        return zones.stream().filter(zone -> zone != excluded).anyMatch(zone -> zone.name().value().equals(name));
    }

    private List<Seat> requireSeats(Set<Long> seatIds) {
        Objects.requireNonNull(seatIds, "seatIds");
        return seatIds.stream().map(seatId -> seat(Objects.requireNonNull(seatId, "seatId"))).toList();
    }

    private static boolean changeEach(List<Seat> seats, SeatChange change) {
        boolean changed = false;
        for (Seat seat : seats) {
            changed |= change.apply(seat);
        }
        return changed;
    }

    private List<Seat> allSeats() {
        return blocks.stream().flatMap(block -> block.seats().stream()).toList();
    }

    private void requireConsistent() {
        Set<String> zoneNames = new HashSet<>();
        Set<Long> zoneIds = new HashSet<>();
        for (Zone zone : zones) {
            Objects.requireNonNull(zone, "zone");
            if (!zoneNames.add(zone.name().value())) {
                throw new IllegalArgumentException("구역 이름이 겹칩니다: 행사 " + eventId + ", " + zone.name().value());
            }
            zone.id().ifPresent(zoneId -> requireUnique(zoneIds, zoneId, "구역"));
        }
        Set<String> blockNames = new HashSet<>();
        Set<Long> blockIds = new HashSet<>();
        Set<Long> seatIds = new HashSet<>();
        for (SeatBlock block : blocks) {
            Objects.requireNonNull(block, "block");
            String blockName = block.name().value();
            if (!blockNames.add(blockName) || zoneNames.contains(blockName)) {
                throw new IllegalArgumentException("블록 이름이 다른 블록이나 구역과 겹칩니다: 행사 " + eventId + ", " + blockName);
            }
            block.id().ifPresent(blockId -> requireUnique(blockIds, blockId, "블록"));
            for (Seat seat : block.seats()) {
                seat.id().ifPresent(seatId -> requireUnique(seatIds, seatId, "좌석"));
                seat.zoneId().ifPresent(zoneId -> requireSeatZone(block, seat, zoneId));
            }
        }
    }

    private void requireSeatZone(SeatBlock block, Seat seat, long zoneId) {
        Zone zone = zones.stream()
                .filter(candidate -> candidate.hasId(zoneId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "도면에 없는 구역에 배정된 좌석입니다: " + block.name().value() + " " + seat.number().text() + ", 구역 " + zoneId));
        if (!zone.takesSeats()) {
            throw new IllegalArgumentException("수량형 구역에 배정된 좌석입니다: " + block.name().value() + " " + seat.number().text());
        }
    }

    private static void requireUnique(Set<Long> seen, long id, String what) {
        if (!seen.add(id)) {
            throw new IllegalArgumentException(what + " id가 겹칩니다: " + id);
        }
    }

    private static void requireSaved(boolean saved, String what) {
        if (!saved) {
            throw new IllegalArgumentException("저장소에서 읽은 도면의 " + what + "에 id가 없습니다");
        }
    }

    @FunctionalInterface
    private interface SeatChange {

        /** @return 바뀌었는지 */
        boolean apply(Seat seat);
    }
}
