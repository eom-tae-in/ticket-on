package com.ticketon.domain.account;

/**
 * 가입하려는 이메일을 쓸 수 있는지 — 저장소가 확인해 가입 규칙({@link AudienceAccount#signUp})에 넘긴다.
 * <p>
 * 같은 이메일의 관객 계정은 하나뿐이다. 이메일이 관객의 로그인 아이디이기 때문이다(docs/01 "수집 정보 — 사용자").
 * 같은 이메일로 운영진 로그인을 따로 만드는 것은 된다 — 관객 계정만 본다(docs/01 "같은 이메일로 양쪽 가입").
 */
public enum EmailAvailability {

    /** 그 이메일의 관객 계정이 없다. */
    AVAILABLE,

    /** 같은 이메일의 관객 계정이 있다. */
    ALREADY_REGISTERED
}
