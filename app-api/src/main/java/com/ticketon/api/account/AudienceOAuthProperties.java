package com.ticketon.api.account;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관객 카카오·구글 로그인 설정({@code ticketon.oauth}) — 제공자마다 클라이언트 id·시크릿, 제공자 주소, 받을 동의 범위.
 * <p>
 * <b>클라이언트 id·시크릿은 환경 변수로만 받는다</b>(KAKAO_CLIENT_ID·KAKAO_CLIENT_SECRET·GOOGLE_CLIENT_ID·GOOGLE_CLIENT_SECRET,
 * .env.example). 레포에 키를 넣지 않는다. 둘 중 하나라도 없는 제공자는 등록하지 않는다 — 서버는 뜨고 그 제공자의 로그인만 열리지 않는다
 * ({@link AudienceOAuthClientConfiguration}).
 * <p>
 * 제공자 주소는 실제 주소를 application.yml에 적어 두고, 테스트는 가짜 제공자 주소로 바꾼다.
 *
 * @param kakao  카카오
 * @param google 구글
 */
@ConfigurationProperties("ticketon.oauth")
public record AudienceOAuthProperties(@Nullable Provider kakao, @Nullable Provider google) {

    /** 그 제공자의 설정. 설정 파일에 칸이 없으면 비어 있다. */
    @Nullable Provider of(OAuthProviderCode code) {
        return switch (code) {
            case KAKAO -> kakao;
            case GOOGLE -> google;
        };
    }

    /**
     * 제공자 하나의 설정.
     *
     * @param clientId         클라이언트 id — 환경 변수로만
     * @param clientSecret     클라이언트 시크릿 — 환경 변수로만
     * @param authorizationUri 관객을 보내 로그인·동의를 받는 제공자 주소
     * @param tokenUri         인가 코드를 토큰으로 바꾸는 제공자 주소
     * @param userInfoUri      토큰으로 사용자 정보를 읽는 제공자 주소
     * @param scopes           받을 동의 범위. OpenID Connect(openid)는 넣지 않는다 — 사용자 정보 주소로 읽는다.
     */
    public record Provider(@Nullable String clientId, @Nullable String clientSecret, @Nullable String authorizationUri,
            @Nullable String tokenUri, @Nullable String userInfoUri, @Nullable List<String> scopes) {

        boolean hasClientId() {
            return hasText(clientId);
        }

        boolean hasClientSecret() {
            return hasText(clientSecret);
        }

        private static boolean hasText(@Nullable String value) {
            return value != null && !value.isBlank();
        }
    }
}
