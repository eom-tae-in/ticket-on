package com.ticketon.api.account;

import com.ticketon.domain.account.OAuthProvider;

/**
 * 카카오·구글 제공자의 API 이름 — 화면 타입과 주소에 쓰인다. 선언 순서가 화면 순서다.
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 제공자({@link OAuthProvider})와 같다.
 */
public enum OAuthProviderCode {

    /** 카카오. */
    KAKAO(OAuthProvider.KAKAO),

    /** 구글. */
    GOOGLE(OAuthProvider.GOOGLE);

    private final OAuthProvider provider;

    OAuthProviderCode(OAuthProvider provider) {
        this.provider = provider;
    }

    OAuthProvider toProvider() {
        return provider;
    }

    static OAuthProviderCode from(OAuthProvider provider) {
        for (OAuthProviderCode code : values()) {
            if (code.provider == provider) {
                return code;
            }
        }
        throw new IllegalArgumentException("API 이름이 없는 제공자: " + provider);
    }
}
