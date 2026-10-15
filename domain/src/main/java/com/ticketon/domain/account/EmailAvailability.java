package com.ticketon.domain.account;

/**
 * 가입하려는 이메일을 쓸 수 있는지 — 저장소가 확인해 가입 규칙({@link AudienceAccount#signUp}, {@link StaffAccount#signUp})과
 * 관리자 계정 발급 규칙({@link AdminAccount#issue})에 넘긴다.
 * <p>
 * <b>같은 종류의 계정 사이에서만 본다.</b> 같은 이메일의 관객 계정은 하나뿐이고(이메일이 관객의 로그인 아이디 — docs/01 "수집 정보 — 사용자"),
 * 같은 이메일의 운영진 로그인도 하나뿐이다. 같은 이메일로 관객 계정과 운영진 로그인을 하나씩 만드는 것은 된다 — 관객 가입은 관객 계정만,
 * 운영진 가입은 운영진 로그인만 본다(docs/01 "같은 이메일로 양쪽 가입"). 관리자 계정도 관리자 계정 사이에서만 본다.
 */
public enum EmailAvailability {

    /** 그 이메일을 쓰는 같은 종류의 계정이 없다. */
    AVAILABLE,

    /** 같은 이메일을 쓰는 같은 종류의 계정이 있다. */
    ALREADY_REGISTERED
}
