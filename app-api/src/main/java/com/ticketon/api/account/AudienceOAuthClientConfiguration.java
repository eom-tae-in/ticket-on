package com.ticketon.api.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * 관객 카카오·구글 로그인의 OAuth2 클라이언트 등록(docs/16 "로그인 — 카카오·구글은 OAuth2").
 * <ul>
 *   <li><b>키가 있는 제공자만 등록한다.</b> 클라이언트 id·시크릿이 둘 다 있어야 등록하고, 없으면 시작할 때 로그에 남긴다. 키가 하나도 없어도
 *       서버는 뜬다 — 등록되지 않은 제공자의 로그인 시작 주소는 404다.</li>
 *   <li>OpenID Connect가 아니라 OAuth2 사용자 정보 주소로 읽는다. 구글도 openid 범위를 쓰지 않는다 — ID 토큰 서명 검증(JWKS) 없이 토큰으로
 *       사용자 정보를 직접 받는다.</li>
 *   <li>카카오는 시크릿을 요청 본문(client_secret_post)으로, 구글은 Basic 인증(client_secret_basic)으로 보낸다. 사용자 id 칸은 카카오
 *       {@code id}(회원번호), 구글 {@code sub}다.</li>
 *   <li>제공자에게 받은 토큰은 보관하지 않는다({@link #discardingAuthorizedClientRepository()}). 로그인할 때 사용자 정보를 읽는 데만 쓴다.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AudienceOAuthProperties.class)
public class AudienceOAuthClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AudienceOAuthClientConfiguration.class);

    @Bean
    public ClientRegistrationRepository audienceOAuthClientRegistrations(AudienceOAuthProperties properties) {
        Map<OAuthProviderCode, ClientRegistration> registrations = new EnumMap<>(OAuthProviderCode.class);
        for (OAuthProviderCode code : OAuthProviderCode.values()) {
            AudienceOAuthProperties.Provider provider = properties.of(code);
            if (isConfigured(code, provider)) {
                registrations.put(code, registrationOf(code, provider));
            }
        }
        log.info("관객 OAuth 로그인 — 켜진 제공자: {}", registrations.keySet());
        return new ConfiguredClientRegistrations(registrations);
    }

    /** 제공자 토큰을 보관하지 않는 저장소 — 로그인을 마치면 토큰은 버려진다. */
    @Bean
    public OAuth2AuthorizedClientRepository discardingAuthorizedClientRepository() {
        return new DiscardingAuthorizedClientRepository();
    }

    private static boolean isConfigured(OAuthProviderCode code, AudienceOAuthProperties.@Nullable Provider provider) {
        if (provider == null || !provider.hasClientId() && !provider.hasClientSecret()) {
            log.info("{} 로그인 꺼짐 — 클라이언트 id·시크릿 환경 변수가 없습니다", code);
            return false;
        }
        if (!provider.hasClientId() || !provider.hasClientSecret()) {
            log.warn("{} 로그인 꺼짐 — 클라이언트 id와 시크릿 중 하나만 있습니다. 둘 다 넣어야 켜집니다", code);
            return false;
        }
        return true;
    }

    private static ClientRegistration registrationOf(OAuthProviderCode code, AudienceOAuthProperties.Provider provider) {
        return ClientRegistration.withRegistrationId(code.registrationId())
                .clientName(code.name())
                .clientId(provider.clientId())
                .clientSecret(provider.clientSecret())
                .clientAuthenticationMethod(clientAuthenticationMethodOf(code))
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(AudienceOAuthSecurityConfiguration.REDIRECT_URI_TEMPLATE)
                .scope(provider.scopes() == null ? List.of() : provider.scopes())
                .authorizationUri(provider.authorizationUri())
                .tokenUri(provider.tokenUri())
                .userInfoUri(provider.userInfoUri())
                .userNameAttributeName(AudienceOAuthProfile.userIdAttributeOf(code))
                .build();
    }

    private static ClientAuthenticationMethod clientAuthenticationMethodOf(OAuthProviderCode code) {
        return switch (code) {
            case KAKAO -> ClientAuthenticationMethod.CLIENT_SECRET_POST;
            case GOOGLE -> ClientAuthenticationMethod.CLIENT_SECRET_BASIC;
        };
    }

    /**
     * 키가 있는 제공자만 담은 등록 목록. 비어 있어도 된다(Spring의 메모리 등록 목록은 빈 목록을 받지 않는다).
     * 목록을 돌며 보는 기능(기본 로그인 화면 링크 등)은 쓰지 않으므로 순회를 열지 않는다.
     */
    static final class ConfiguredClientRegistrations implements ClientRegistrationRepository {

        private final Map<String, ClientRegistration> registrationsById = new LinkedHashMap<>();

        ConfiguredClientRegistrations(Map<OAuthProviderCode, ClientRegistration> registrations) {
            registrations.forEach((code, registration) -> registrationsById.put(code.registrationId(), registration));
        }

        @Override
        public @Nullable ClientRegistration findByRegistrationId(String registrationId) {
            return registrationsById.get(registrationId);
        }
    }

    /** 제공자 토큰을 보관하지 않는다 — 저장은 아무것도 하지 않고, 꺼내면 늘 없다. */
    static final class DiscardingAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

        @Override
        public <T extends OAuth2AuthorizedClient> @Nullable T loadAuthorizedClient(String clientRegistrationId,
                Authentication principal, HttpServletRequest request) {
            return null;
        }

        @Override
        public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
                HttpServletRequest request, HttpServletResponse response) {
            log.debug("제공자 토큰은 보관하지 않습니다: {}", authorizedClient.getClientRegistration().getRegistrationId());
        }

        @Override
        public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
                HttpServletRequest request, HttpServletResponse response) {
            log.debug("보관한 제공자 토큰이 없습니다: {}", clientRegistrationId);
        }
    }
}
