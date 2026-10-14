package com.ticketon.domain.error;

import java.util.Objects;

/**
 * 도메인 공통 예외의 바탕. 오류 코드 하나를 담는다.
 * <p>
 * 종류는 네 가지로 닫혀 있다(규칙 위반·찾을 수 없음·권한 없음·충돌). 들어오는 어댑터는 종류마다 정해진 상태로 응답하므로,
 * 새 종류가 필요하면 어댑터의 응답 규칙과 함께 정한다.
 * <p>
 * 화면에는 {@link ErrorCode#message()}만 나간다. {@link #getMessage()}는 로그용으로 코드와 detail을 담는다.
 */
public abstract sealed class DomainException extends RuntimeException
        permits RuleViolationException, NotFoundException, PermissionDeniedException, ConflictException {

    private final ErrorCode errorCode;

    protected DomainException(ErrorCode errorCode) {
        this(errorCode, null);
    }

    /**
     * @param detail 로그에만 남기는 설명(어느 회차·좌석인지 등). 화면에 내보내지 않는다. 없으면 null.
     */
    protected DomainException(ErrorCode errorCode, String detail) {
        super(logMessage(errorCode, detail));
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    private static String logMessage(ErrorCode errorCode, String detail) {
        Objects.requireNonNull(errorCode, "errorCode");
        return detail == null ? errorCode.code() : errorCode.code() + " — " + detail;
    }
}
