package com.ticketon.domain.error;

/**
 * 권한 없음 — 누구인지는 알지만 그 대상에 그 일을 할 권한이 없다.
 * <p>
 * 예: 운영자가 회차를 취소하려 함 — 배상이 걸린 일은 소유자만 한다(docs/01 "권한 두 가지").
 * 로그인하지 않은 요청(미인증)은 이 예외가 아니다. 들어오는 어댑터가 따로 다룬다.
 */
public final class PermissionDeniedException extends DomainException {

    public PermissionDeniedException(ErrorCode errorCode) {
        super(errorCode);
    }

    public PermissionDeniedException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
