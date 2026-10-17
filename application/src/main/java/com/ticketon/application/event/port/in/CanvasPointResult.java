package com.ticketon.application.event.port.in;

import com.ticketon.domain.event.CanvasPoint;

/**
 * 배치도 위의 한 점 — 블록을 놓은 자리, 무대 자리(docs/02 "에디터는 블록 단위"). 단위와 원점은 배치도 화면이 정한다.
 *
 * @param x 가로 좌표
 * @param y 세로 좌표
 */
public record CanvasPointResult(int x, int y) {

    public static CanvasPointResult of(CanvasPoint point) {
        return new CanvasPointResult(point.x(), point.y());
    }
}
