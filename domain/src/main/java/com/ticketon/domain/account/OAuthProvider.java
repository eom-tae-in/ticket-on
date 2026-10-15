package com.ticketon.domain.account;

/**
 * 관객이 가입·로그인에 쓰는 OAuth 제공자 — 카카오·구글(docs/01 "가입 경로 — OAuth", docs/16 "로그인").
 * <p>
 * <b>OAuth는 편의 기능이지 본인확인이 아니다</b>(docs/01 "OAuth는 중복 계정을 막지 못합니다"). 제공자 계정은 누구나 여러 개 만들 수 있으므로
 * OAuth로 가입해도 전화번호 중복 확인을 똑같이 거친다({@link AudienceAccount#signUpThroughOAuth}).
 * <p>
 * 이 이름은 저장소에 그대로 남으므로 바꾸지 않는다.
 */
public enum OAuthProvider {

    /** 카카오. */
    KAKAO(AccountErrorCode.KAKAO_SIGNUP_EMAIL_ALREADY_REGISTERED),

    /** 구글. */
    GOOGLE(AccountErrorCode.GOOGLE_SIGNUP_EMAIL_ALREADY_REGISTERED);

    private final AccountErrorCode emailAlreadyRegisteredError;

    OAuthProvider(AccountErrorCode emailAlreadyRegisteredError) {
        this.emailAlreadyRegisteredError = emailAlreadyRegisteredError;
    }

    /**
     * 이 제공자로 가입하려는데 같은 이메일의 관객 계정이 이미 있을 때의 오류. 자동으로 묶지 않고, 기존 계정으로 로그인한 뒤 이 제공자 계정을
     * 연결하라고 안내한다(docs/01 "계정 연동"). 안내 문구가 제공자 이름을 담아 제공자마다 코드가 다르다.
     */
    public AccountErrorCode emailAlreadyRegisteredError() {
        return emailAlreadyRegisteredError;
    }
}
