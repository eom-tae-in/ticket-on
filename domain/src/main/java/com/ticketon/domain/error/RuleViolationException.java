package com.ticketon.domain.error;

/**
 * 규칙 위반 — 지금 상태에서는 그 요청을 받아들일 수 없다.
 * <p>
 * 예: 이 행사에서 쓸 수 없는 쿠폰 코드 "이 행사에서 사용할 수 없는 코드입니다"(docs/05 "잘못된 코드"),
 * 이미 가입된 전화번호 "이미 가입된 번호입니다"(docs/01).
 */
public final class RuleViolationException extends DomainException {

    public RuleViolationException(ErrorCode errorCode) {
        super(errorCode);
    }

    public RuleViolationException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}
