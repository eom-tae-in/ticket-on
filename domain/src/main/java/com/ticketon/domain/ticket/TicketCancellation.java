package com.ticketon.domain.ticket;

import java.time.Instant;
import java.util.Objects;

/**
 * 티켓의 취소 기록 — 취소 시각과 사유(docs/07 "티켓은 '취소됐나'만 기록합니다"). 티켓에 이 기록이 없으면 유효, 있으면 그 시각에 취소된 것이다.
 *
 * @param cancelledAt 취소 시각
 * @param reason      취소 사유
 */
public record TicketCancellation(Instant cancelledAt, TicketCancellationReason reason) {

    public TicketCancellation {
        Objects.requireNonNull(cancelledAt, "cancelledAt");
        Objects.requireNonNull(reason, "reason");
    }
}
