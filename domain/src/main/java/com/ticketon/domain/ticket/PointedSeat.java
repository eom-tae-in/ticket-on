package com.ticketon.domain.ticket;

import com.ticketon.domain.event.BlockName;
import com.ticketon.domain.event.SeatLabel;
import com.ticketon.domain.event.SeatNumber;
import java.util.Objects;

/**
 * 티켓이 가리키는 좌석 — 좌석 표기를 만들 때 따라가 읽는 블록 이름 · 좌석 번호와, 그 블록이 지워졌는지.
 * <p>
 * 티켓(예매 · 초대권)이 걸린 좌석은 지울 수 없지만, <b>취소 · 회수된 티켓만 남은 블록은 지울 수 있다.</b> 지워도 기록으로 남아 그 티켓에는 원래
 * 번호가 그대로 보인다(docs/07 "티켓이 직접 갖는 것과 가리키기만 하는 것", docs/14 "도면은 행사마다 따로 갖습니다"). 지운 블록의 이름은 그 블록
 * 기록에 남은 마지막 이름이다.
 * <p>
 * 좌석 표기는 도면의 지금 블록 수가 1이면 블록 이름을 뺀다({@link SeatLabel}). <b>지운 블록의 좌석은 블록 수와 상관없이 블록 이름을 붙인다</b> —
 * 지운 블록은 지금 도면에 없어, 남은 블록이 하나라고 이름을 빼면 취소된 티켓의 "12열 7번"이 남은 블록의 같은 번호 좌석처럼 보인다. 확인 필요
 * (#56, #37 인계) — 지운 블록의 블록 이름 · 블록 수를 무엇으로 볼지는 문서에 없다.
 *
 * @param blockName    좌석이 있는(있던) 블록의 이름
 * @param seatNumber   블록 안의 좌석 번호
 * @param blockRemoved 그 블록이 지워졌는지
 */
public record PointedSeat(BlockName blockName, SeatNumber seatNumber, boolean blockRemoved) {

    /** 블록 이름을 붙이는 표기를 고르는 블록 수 — {@link SeatLabel#of}는 블록이 둘 이상이면 이름을 붙인다. */
    private static final int BLOCK_COUNT_WITH_NAME = 2;

    public PointedSeat {
        Objects.requireNonNull(blockName, "blockName");
        Objects.requireNonNull(seatNumber, "seatNumber");
    }

    /**
     * 좌석 표기 — "12열 7번" 또는 "1층 12열 7번".
     *
     * @param currentBlockCount 도면의 지금 블록 수(지운 블록 제외). 살아 있는 블록의 좌석이면 1 이상이다
     * @throws IllegalArgumentException 살아 있는 블록의 좌석인데 블록 수가 1보다 적으면
     */
    public SeatLabel label(int currentBlockCount) {
        if (currentBlockCount < 0) {
            throw new IllegalArgumentException("블록 수는 0 이상이어야 합니다: " + currentBlockCount);
        }
        return SeatLabel.of(blockName, seatNumber, blockRemoved ? BLOCK_COUNT_WITH_NAME : currentBlockCount);
    }
}
