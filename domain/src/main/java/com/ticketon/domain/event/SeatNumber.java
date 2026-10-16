package com.ticketon.domain.event;

/**
 * 좌석 번호 — 블록 안에서 그 자리가 몇 열 몇 번인가("12열 7번", docs/02 "좌석 번호 표기"). 블록이 좌석 번호를 유일하게 만드는 단위라 같은 번호가
 * 블록마다 하나씩 있을 수 있고, 블록이 둘 이상이면 블록 이름을 앞에 붙여 가른다({@link SeatLabel}).
 * <p>
 * 확인 필요 (#37) — 번호는 블록을 얹을 때 격자 자리로 정해지고 다시 매기지 않는다({@code SeatBlock}). 열은 블록 격자의 행 순번, 번은 그 행 안의
 * 순번이다(1부터, {@link BlockSize}).
 *
 * @param row    몇 열인가 — 블록 격자의 행 순번, 1 이상
 * @param number 몇 번인가 — 그 행 안의 순번, 1 이상
 */
public record SeatNumber(int row, int number) {

    public SeatNumber {
        if (row < 1 || number < 1) {
            throw new IllegalArgumentException("좌석 번호의 열과 번은 1 이상이어야 합니다: " + row + "열 " + number + "번");
        }
    }

    public static SeatNumber of(int row, int number) {
        return new SeatNumber(row, number);
    }

    /** 블록 이름을 뺀 표기 — "12열 7번". */
    public String text() {
        return row + "열 " + number + "번";
    }
}
