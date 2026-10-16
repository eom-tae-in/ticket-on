package com.ticketon.domain.event;

/**
 * 배치도 위의 위치 — 블록을 놓은 자리, 무대 자리(docs/02 "에디터는 블록 단위" — 블록 위치 이동, 무대 위치 지정). 픽셀 단위 자유 배치가 아니라
 * 블록 덩어리를 옮기는 에디터라, 위치는 블록 · 무대마다 하나다. 좌석 하나하나는 위치를 갖지 않고 블록 안의 행 · 열로 놓인다.
 * <p>
 * 좌표의 단위와 원점은 배치도 화면이 정한다. 도면은 받은 값을 그대로 지킨다.
 *
 * @param x 가로 좌표
 * @param y 세로 좌표
 */
public record CanvasPoint(int x, int y) {

    /** 원점 — 위치를 정하지 않고 얹은 블록의 자리. */
    public static final CanvasPoint ORIGIN = new CanvasPoint(0, 0);

    public static CanvasPoint of(int x, int y) {
        return new CanvasPoint(x, y);
    }
}
