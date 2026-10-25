package com.ticketon.domain.ticket;

import java.util.Objects;
import java.util.Optional;

/**
 * 관객 화면에 그릴 티켓 하나의 모습 — 상태, 취소됐으면 사유, QR · 공유 버튼을 그릴지(docs/07 "관객 화면의 표시는 계산됩니다", "화면에서는 버튼만
 * 빠집니다"). {@link Ticket#display}가 만든다.
 *
 * @param status             보이는 상태
 * @param cancellationReason 취소 사유 — 취소됨일 때만 있다
 */
public record TicketDisplay(TicketDisplayStatus status, Optional<TicketCancellationReason> cancellationReason) {

    private static final String SEPARATOR = " · ";
    private static final String RECALLED_LABEL = "회수됨";

    public TicketDisplay {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(cancellationReason, "cancellationReason");
        if ((status == TicketDisplayStatus.CANCELLED) != cancellationReason.isPresent()) {
            throw new IllegalArgumentException("취소 사유는 취소됨에만 붙습니다: " + status + ", " + cancellationReason);
        }
    }

    /** 취소되지 않은 티켓의 모습 — 유효 · 입장 완료 · 공연 종료. */
    static TicketDisplay of(TicketDisplayStatus status) {
        return new TicketDisplay(status, Optional.empty());
    }

    /** 취소된 티켓의 모습. */
    static TicketDisplay cancelled(TicketCancellationReason reason) {
        return new TicketDisplay(TicketDisplayStatus.CANCELLED, Optional.of(reason));
    }

    /**
     * QR · 공유 버튼을 그릴지 — 취소되지 않았고 가리키는 회차가 아직 다 끝나지 않았으면(유효 · 입장 완료). 취소된 티켓과 공연이 끝난 티켓에는
     * 버튼이 없다. 판단은 티켓마다다 — 8회차 행사에서 3/14 표는 3/14 공연이 끝나면 버튼이 사라지고, 3일권은 마지막 날이 끝나야 사라진다.
     */
    public boolean showsQrAndShareButtons() {
        return status == TicketDisplayStatus.VALID || status == TicketDisplayStatus.ENTERED;
    }

    /**
     * 화면에 보이는 글 — "유효", "입장 완료", "공연 종료", 취소됐으면 "취소됨 · 관객 취소"(docs/07 예매 카드). 초대권 회수는 "회수됨"이다 — 단체 링크
     * 화면에서 회수된 줄이 회색 "회수됨"으로 남는다(docs/09 "단체 링크 화면"). 회차 취소 등으로 취소된 초대권은 예매와 같이 "취소됨 · 회차 취소"다.
     */
    public String label() {
        return cancellationReason
                .map(reason -> reason == TicketCancellationReason.INVITATION_RECALLED
                        ? RECALLED_LABEL
                        : status.label() + SEPARATOR + reason.label())
                .orElse(status.label());
    }
}
