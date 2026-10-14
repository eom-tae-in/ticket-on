package com.ticketon.api.error;

/**
 * 미인증 — 로그인이 필요한 요청에 로그인 정보가 없다. 401로 응답한다.
 * <p>
 * 들어오는 어댑터가 로그인 사용자를 Command에 담을 때 로그인 정보가 없으면 던진다. 도메인은 로그인을 모르므로 domain/error에 두지 않는다.
 */
public final class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super(ApiErrorCode.UNAUTHENTICATED.code());
    }
}
