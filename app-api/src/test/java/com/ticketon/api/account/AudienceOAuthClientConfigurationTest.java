package com.ticketon.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@DisplayName("관객 카카오·구글 OAuth2 클라이언트 등록")
class AudienceOAuthClientConfigurationTest {

    private final AudienceOAuthClientConfiguration configuration = new AudienceOAuthClientConfiguration();

    @Test
    @DisplayName("카카오 — 시크릿은 요청 본문으로 보내고, 사용자 id는 id 칸(회원번호)이며, 콜백은 /api/auth/audience/oauth2/callback/kakao다")
    void kakaoRegistration() {
        ClientRegistration kakao = registrationsOf(provider("kakao-id", "kakao-secret", List.of("account_email")), null)
                .findByRegistrationId("kakao");

        assertThat(kakao).isNotNull();
        assertThat(kakao.getClientId()).isEqualTo("kakao-id");
        assertThat(kakao.getClientSecret()).isEqualTo("kakao-secret");
        assertThat(kakao.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_POST);
        assertThat(kakao.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.AUTHORIZATION_CODE);
        assertThat(kakao.getRedirectUri()).isEqualTo("{baseUrl}/api/auth/audience/oauth2/callback/{registrationId}");
        assertThat(kakao.getScopes()).containsExactly("account_email");
        assertThat(kakao.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("id");
        assertThat(kakao.getProviderDetails().getTokenUri()).isEqualTo("https://provider.example/token");
    }

    @Test
    @DisplayName("구글 — 시크릿은 Basic 인증으로 보내고, 사용자 id는 sub 칸이며, openid 범위는 없다")
    void googleRegistration() {
        ClientRegistration google = registrationsOf(null, provider("google-id", "google-secret", List.of("profile", "email")))
                .findByRegistrationId("google");

        assertThat(google).isNotNull();
        assertThat(google.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
        assertThat(google.getScopes()).containsExactlyInAnyOrder("profile", "email").doesNotContain("openid");
        assertThat(google.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("sub");
    }

    @Test
    @DisplayName("키가 없는 제공자는 등록하지 않는다 — 하나도 없어도 등록 목록은 만들어진다")
    void skipsProviderWithoutKeys() {
        ClientRegistrationRepository registrations = registrationsOf(provider("", "", List.of()), null);

        assertThat(registrations.findByRegistrationId("kakao")).isNull();
        assertThat(registrations.findByRegistrationId("google")).isNull();
    }

    @Test
    @DisplayName("클라이언트 id와 시크릿 중 하나만 있으면 등록하지 않는다")
    void skipsProviderWithHalfKeys() {
        ClientRegistrationRepository registrations = registrationsOf(provider("kakao-id", " ", List.of()),
                provider(null, "google-secret", List.of()));

        assertThat(registrations.findByRegistrationId("kakao")).isNull();
        assertThat(registrations.findByRegistrationId("google")).isNull();
    }

    @Test
    @DisplayName("모르는 등록 id는 없다")
    void unknownRegistrationId() {
        assertThat(registrationsOf(provider("kakao-id", "kakao-secret", List.of()), null).findByRegistrationId("naver"))
                .isNull();
    }

    private ClientRegistrationRepository registrationsOf(AudienceOAuthProperties.Provider kakao,
            AudienceOAuthProperties.Provider google) {
        return configuration.audienceOAuthClientRegistrations(new AudienceOAuthProperties(kakao, google));
    }

    private static AudienceOAuthProperties.Provider provider(String clientId, String clientSecret, List<String> scopes) {
        return new AudienceOAuthProperties.Provider(clientId, clientSecret, "https://provider.example/authorize",
                "https://provider.example/token", "https://provider.example/userinfo", scopes);
    }
}
