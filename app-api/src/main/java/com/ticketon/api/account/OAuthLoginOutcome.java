package com.ticketon.api.account;

/**
 * 카카오·구글 로그인 한 번의 결과 — 화면 타입에 쓰인다.
 */
public enum OAuthLoginOutcome {

    /** 연결된 관객 계정으로 로그인됐다. 화면은 돌아갈 경로로 간다. */
    LOGGED_IN,

    /** 연결된 관객 계정이 없다 — 추가 정보 입력 화면을 보여 주고 가입 API로 가입한다. */
    SIGNUP_REQUIRED,

    /** 로그인한 관객의 계정에 연결했다(마이페이지 OAuth 연동). 로그인 주체는 그대로다. */
    LINKED,

    /** 로그인·연결하지 못했다. 까닭은 error에 있다. */
    FAILED
}
