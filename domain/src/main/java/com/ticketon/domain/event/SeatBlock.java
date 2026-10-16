package com.ticketon.domain.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/**
 * 블록 — 좌석 번호를 유일하게 만드는 덩어리(docs/02 "블록", docs/14 "1층 — 도면"). 블록이 둘인데 이름이 없으면 양쪽에 "1열 7번"이 생겨 구분이
 * 안 된다. 이름({@link BlockName}), 행 × 열 크기({@link BlockSize}), 배치도 위의 위치({@link CanvasPoint})와 각도({@link BlockAngle}), 그리고
 * 행 × 열만큼의 좌석을 갖는다.
 * <p>
 * <b>블록을 얹으면 행 × 열 격자의 모든 자리에 좌석이 생긴다</b>(docs/02 "블록 추가 = 이름 + '5행 × 10열' 덩어리 얹기"). 확인 필요 (#37) — 좌석
 * 번호는 이때 격자 자리로 정한다. r번째 행 c번째 자리가 "r열 c번"이다(1부터). 이 번호는 다시 매기지 않는다 — 블록을 옮기거나 돌려도, 블록 이름을
 * 고쳐도, 다른 좌석을 비활성화해도 그대로다({@link Seat}). 통로 · 기둥 자리는 지우지 않고 비활성으로 둔다.
 * <p>
 * 블록 크기를 바꾸는 길은 두지 않았다 — 문서의 에디터는 덩어리 얹기 · 위치 이동 · 각도 조절 · 개별 좌석 비활성화까지다(docs/02 "에디터는 블록
 * 단위").
 * <p>
 * 바꾸는 일은 도면이 한다({@link FloorPlan}). 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 블록 · 좌석은 id가 없다.
 */
public final class SeatBlock {

    private final Long id;
    private BlockName name;
    private final BlockSize size;
    private CanvasPoint position;
    private BlockAngle angle;
    private final List<Seat> seats;

    private SeatBlock(Long id, BlockName name, BlockSize size, CanvasPoint position, BlockAngle angle, List<Seat> seats) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.size = Objects.requireNonNull(size, "size");
        this.position = Objects.requireNonNull(position, "position");
        this.angle = Objects.requireNonNull(angle, "angle");
        this.seats = new ArrayList<>(seats);
        requireFullGrid();
    }

    /** 새 블록 — 행 × 열 격자의 모든 자리에 활성 · 일반 · 구역 없는 좌석이 생긴다. 행 순서, 같은 행은 번호 순서. */
    static SeatBlock newBlock(BlockName name, BlockSize size, CanvasPoint position, BlockAngle angle) {
        Objects.requireNonNull(size, "size");
        List<Seat> seats = new ArrayList<>(size.seatCount());
        for (int row = 1; row <= size.rows(); row++) {
            for (int number = 1; number <= size.columns(); number++) {
                seats.add(Seat.newSeat(SeatNumber.of(row, number)));
            }
        }
        return new SeatBlock(null, name, size, position, angle, seats);
    }

    /**
     * 저장소에서 읽은 값으로 블록을 되살린다. 좌석은 격자의 모든 자리에 하나씩 있어야 한다.
     *
     * @throws IllegalArgumentException 좌석 번호가 크기 밖이거나, 같은 번호가 둘이거나, 빈 자리가 있으면
     */
    public static SeatBlock restore(long id, BlockName name, BlockSize size, CanvasPoint position, BlockAngle angle, List<Seat> seats) {
        if (id < 1) {
            throw new IllegalArgumentException("블록 id는 1 이상이어야 합니다: " + id);
        }
        Objects.requireNonNull(seats, "seats");
        return new SeatBlock(id, name, size, position, angle, seats);
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public BlockName name() {
        return name;
    }

    public BlockSize size() {
        return size;
    }

    public CanvasPoint position() {
        return position;
    }

    public BlockAngle angle() {
        return angle;
    }

    /** 이 블록의 좌석 — 행 × 열 격자의 모든 자리. 비활성 좌석도 들어 있다. */
    public List<Seat> seats() {
        return Collections.unmodifiableList(seats);
    }

    boolean hasId(long blockId) {
        return id != null && id == blockId;
    }

    Optional<Seat> findSeat(long seatId) {
        return seats.stream().filter(seat -> seat.hasId(seatId)).findFirst();
    }

    boolean contains(Seat seat) {
        return seats.stream().anyMatch(candidate -> candidate == seat);
    }

    /** @return 바뀌었는지 */
    boolean rename(BlockName newName) {
        Objects.requireNonNull(newName, "newName");
        if (newName.equals(name)) {
            return false;
        }
        name = newName;
        return true;
    }

    /** @return 바뀌었는지 */
    boolean moveTo(CanvasPoint newPosition) {
        Objects.requireNonNull(newPosition, "newPosition");
        if (newPosition.equals(position)) {
            return false;
        }
        position = newPosition;
        return true;
    }

    /** @return 바뀌었는지 */
    boolean rotateTo(BlockAngle newAngle) {
        Objects.requireNonNull(newAngle, "newAngle");
        if (newAngle.equals(angle)) {
            return false;
        }
        angle = newAngle;
        return true;
    }

    private void requireFullGrid() {
        Set<SeatNumber> seenNumbers = new HashSet<>();
        for (Seat seat : seats) {
            Objects.requireNonNull(seat, "seat");
            if (!size.contains(seat.number())) {
                throw new IllegalArgumentException("블록 크기 밖의 좌석입니다: " + name.value() + " " + seat.number().text());
            }
            if (!seenNumbers.add(seat.number())) {
                throw new IllegalArgumentException("같은 번호의 좌석이 둘입니다: " + name.value() + " " + seat.number().text());
            }
        }
        if (seenNumbers.size() != size.seatCount()) {
            throw new IllegalArgumentException("블록 격자에 좌석이 없는 자리가 있습니다: " + name.value() + " — 좌석 " + seenNumbers.size()
                    + "개, 자리 " + size.seatCount() + "개");
        }
    }
}
