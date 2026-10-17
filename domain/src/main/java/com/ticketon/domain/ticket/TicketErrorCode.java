package com.ticketon.domain.ticket;

import com.ticketon.domain.error.ErrorCode;

/**
 * 티켓 도메인의 오류 코드. 문구는 화면에 그대로 나간다.
 */
public enum TicketErrorCode implements ErrorCode {

    /**
     * 이미 취소된 티켓이다 — 다시 취소하거나, 취소된 티켓의 QR을 재발행하거나 날짜를 포기할 수 없다. 취소 버튼을 두 번 누르거나 다른 취소와 겹칠 때
     * 생긴다. 확인 필요 (#56) — 문구는 문서에 없다.
     */
    TICKET_ALREADY_CANCELLED("이미 취소된 티켓입니다.");

    private final String message;

    TicketErrorCode(String message) {
        this.message = message;
    }

    @Override
    public String message() {
        return message;
    }
}
