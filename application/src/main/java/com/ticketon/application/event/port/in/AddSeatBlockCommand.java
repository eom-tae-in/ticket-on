package com.ticketon.application.event.port.in;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.BlockAngle;
import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.BlockSize;
import com.ticketon.domain.event.CanvasPoint;
import java.util.Objects;

/**
 * 블록 얹기 입력 — 이름 + "5행 × 10열" 덩어리(docs/02 "에디터는 블록 단위").
 *
 * @param actingStaff 얹는 운영진과 주최자
 * @param eventId     행사 id
 * @param name        블록 이름. 비웠으면 null — "블록 N"을 붙인다
 * @param size        행 × 열
 * @param position    배치도 위의 위치
 * @param angle       돌린 각도
 */
public record AddSeatBlockCommand(ActingStaff actingStaff, long eventId, BlockName name, BlockSize size, CanvasPoint position,
        BlockAngle angle) {

    public AddSeatBlockCommand {
        Objects.requireNonNull(actingStaff, "actingStaff");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(angle, "angle");
    }
}
