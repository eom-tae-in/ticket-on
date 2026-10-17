package com.ticketon.application.event.port.in;

import java.util.List;
import java.util.Objects;

/**
 * 블록 하나와 그 좌석 — 배치도 에디터의 덩어리(docs/02 "블록", "에디터는 블록 단위").
 *
 * @param blockId      블록 id
 * @param name         이름 — 앞뒤 공백을 걷고 NFC로 맞춘 값. 비워 얹었으면 "블록 N"
 * @param rows         행 수(가로줄 수) — 좌석 표기의 "N열"이 몇까지인가
 * @param columns      열 수(한 줄의 자리 수) — 좌석 표기의 "N번"이 몇까지인가
 * @param position     배치도 위의 위치
 * @param angleDegrees 돌린 각도 — 0 이상 360 미만의 도
 * @param seats        좌석 — 행 × 열 격자의 모든 자리(비활성 좌석도). 행 순서, 같은 행은 번호 순서
 */
public record SeatBlockResult(long blockId, String name, int rows, int columns, CanvasPointResult position, int angleDegrees,
        List<SeatResult> seats) {

    public SeatBlockResult {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(position, "position");
        seats = List.copyOf(Objects.requireNonNull(seats, "seats"));
    }
}
