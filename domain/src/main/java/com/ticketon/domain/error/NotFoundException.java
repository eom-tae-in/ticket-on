package com.ticketon.domain.error;

/**
 * 찾을 수 없음 — 요청이 가리킨 대상(행사·회차·티켓 등)이 없다.
 */
public final class NotFoundException extends DomainException {

    public NotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public NotFoundException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
