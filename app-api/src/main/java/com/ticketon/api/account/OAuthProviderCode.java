package com.ticketon.api.account;

import com.ticketon.domain.account.OAuthProvider;
import java.util.Optional;

/**
 * 카카오·구글 제공자의 API 이름 — 화면 타입과 주소에 쓰인다. 선언 순서가 화면 순서다.
 * <p>
 * 도메인 값을 바깥에 그대로 내보내지 않으려고 따로 둔다. 이름은 도메인 제공자({@link OAuthProvider})와 같다.
 * <p>
 * 로그인 시작·콜백 주소에는 소문자 등록 id({@link #registrationId()} — {@code kakao}, {@code google})를 쓴다. 콜백 주소는 제공자 개발자
 * 콘솔에 그대로 등록하는 값이라 바꾸지 않는다(AudienceOAuthSecurityConfiguration).
 */
public enum OAuthProviderCode {

    /** 카카오. */
    KAKAO(OAuthProvider.KAKAO, "kakao"),

    /** 구글. */
    GOOGLE(OAuthProvider.GOOGLE, "google");

    private final OAuthProvider provider;
    private final String registrationId;

    OAuthProviderCode(OAuthProvider provider, String registrationId) {
        this.provider = provider;
        this.registrationId = registrationId;
    }

    OAuthProvider toProvider() {
        return provider;
    }

    /** OAuth 클라이언트 등록 id — 로그인 시작·콜백 주소의 마지막 칸. */
    String registrationId() {
        return registrationId;
    }

    /** 등록 id의 제공자. 모르는 id면 비어 있다. */
    static Optional<OAuthProviderCode> fromRegistrationId(String registrationId) {
        for (OAuthProviderCode code : values()) {
            if (code.registrationId.equals(registrationId)) {
                return Optional.of(code);
            }
        }
        return Optional.empty();
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
