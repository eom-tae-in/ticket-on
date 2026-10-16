package com.ticketon.domain.event;

import com.ticketon.domain.policy.ProposedPolicy;

/**
 * 블록의 크기 — "5행 × 10열" 덩어리(docs/02 "에디터는 블록 단위"). 블록을 얹으면 행 × 열만큼 좌석이 생긴다 — 10행 × 20열이면 200석이다.
 * <p>
 * <b>에디터의 "행"이 좌석 표기의 "열"이다.</b> 에디터는 덩어리를 "5행 × 10열"(가로줄 5개, 한 줄에 10석)로 받고, 좌석 표기 "12열 7번"은 12번째
 * 가로줄의 7번째 자리다(docs/02 "좌석 번호 표기"). 그래서 행 순번이 표기의 "N열", 열 순번이 "N번"이 된다({@link SeatNumber}).
 * <p>
 * 행은 1~{@value ProposedPolicy#SEAT_BLOCK_MAXIMUM_ROWS}, 열은 1~{@value ProposedPolicy#SEAT_BLOCK_MAXIMUM_COLUMNS}이다
 * ({@link ProposedPolicy#SEAT_BLOCK_MAXIMUM_ROWS}, {@link ProposedPolicy#SEAT_BLOCK_MAXIMUM_COLUMNS}, 확인 필요).
 *
 * @param rows    행(가로줄) 수
 * @param columns 열(한 줄의 자리) 수
 */
public record BlockSize(int rows, int columns) {

    public BlockSize {
        if (!isValid(rows, columns)) {
            throw new IllegalArgumentException("블록은 1~" + ProposedPolicy.SEAT_BLOCK_MAXIMUM_ROWS + "행 × 1~"
                    + ProposedPolicy.SEAT_BLOCK_MAXIMUM_COLUMNS + "열이어야 합니다: " + rows + "행 × " + columns + "열");
        }
    }

    /**
     * 행 × 열 크기를 만든다.
     *
     * @throws IllegalArgumentException 행이나 열이 1보다 적거나 상한을 넘으면
     */
    public static BlockSize of(int rows, int columns) {
        return new BlockSize(rows, columns);
    }

    /** {@link #of}로 만들 수 있는 값인지. 입력 검증에서 같은 규칙을 쓰려고 둔다. */
    public static boolean isValid(int rows, int columns) {
        return rows >= 1 && rows <= ProposedPolicy.SEAT_BLOCK_MAXIMUM_ROWS
                && columns >= 1 && columns <= ProposedPolicy.SEAT_BLOCK_MAXIMUM_COLUMNS;
    }

    /** 블록의 좌석 수 — 행 × 열. 비활성 좌석도 센다. */
    public int seatCount() {
        return rows * columns;
    }

    /** 그 좌석 번호가 이 크기 안에 있는지 — 열 번호가 행 수 이하, 번 번호가 열 수 이하. */
    boolean contains(SeatNumber seatNumber) {
        return seatNumber.row() <= rows && seatNumber.number() <= columns;
    }
}
