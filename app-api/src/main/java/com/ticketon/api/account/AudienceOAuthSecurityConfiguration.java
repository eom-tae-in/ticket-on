package com.ticketon.api.account;

import com.ticketon.api.security.SecurityErrorResponseHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.LogoutConfigurer;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * 관객 카카오·구글 로그인 필터 — 로그인 시작과 제공자에서 돌아오는 두 주소만 맡는 보안 필터 묶음.
 * <table>
 *   <caption>주소</caption>
 *   <tr><th>주소</th><th>하는 일</th></tr>
 *   <tr><td>{@code GET }{@value #AUTHORIZATION_BASE_URI}{@code /{kakao|google}?returnPath=…}</td>
 *       <td>로그인 시작 — 제공자 로그인·동의 화면으로 보낸다(302). 화면은 이 주소로 브라우저를 옮긴다. 키가 없어 꺼진 제공자는 404.</td></tr>
 *   <tr><td>{@code GET }{@value #CALLBACK_BASE_URI}{@code /{kakao|google}}</td>
 *       <td>제공자가 돌려보내는 주소(콜백) — 제공자 개발자 콘솔에 {@code {서버 주소}}{@value #CALLBACK_BASE_URI}{@code /kakao}처럼
 *           등록한다. 끝나면 화면의 결과 페이지로 보낸다({@link AudienceOAuthLoginHandler}).</td></tr>
 * </table>
 * 나머지 API(결과·가입·연결·해제)는 컨트롤러이고 기본 보안 필터(SecurityConfiguration)가 맡는다. 이 묶음이 먼저 걸려
 * 두 주소는 기본 필터의 "그 밖의 경로는 막음"에 닿지 않는다. 기본 필터와 같은 세션 저장소·CSRF 토큰 저장소·오류 응답을 쓴다.
 * <p>
 * <b>제공자 인증 정보는 세션에 저장하지 않는다.</b> 제공자 로그인은 우리 관객 로그인이 아니고, 세션 주체는 늘 "종류:id"다
 * (AccountAuthentication). 그래서 이 묶음은 세션의 로그인 정보를 읽기만 한다({@link ReadOnlySecurityContextRepository}) — Spring의 OAuth2
 * 로그인 필터는 인증을 마치면 이 묶음의 저장소에 제공자 인증 정보를 저장하려 하는데, 아무것도 저장되지 않는다. 로그인할 관객 계정이 정해지면
 * 처리기가 SessionLogin(공용 저장소)으로 세션을 연다. 제공자에서 돌아올 때 세션 id는 바뀌고(세션 고정 방지) CSRF 토큰은 새로 받는다.
 */
@Configuration(proxyBeanMethods = false)
public class AudienceOAuthSecurityConfiguration {

    /** 로그인 시작 주소 — 끝에 제공자 등록 id(kakao, google)를 붙인다. */
    static final String AUTHORIZATION_BASE_URI = "/api/auth/audience/oauth2/authorization";

    /** 제공자가 돌려보내는 주소 — 끝에 제공자 등록 id를 붙인다. */
    static final String CALLBACK_BASE_URI = "/api/auth/audience/oauth2/callback";

    /** 제공자에 보내는 콜백 주소 틀 — 요청이 들어온 서버 주소({baseUrl}) 뒤에 콜백 주소와 등록 id를 붙인다. */
    static final String REDIRECT_URI_TEMPLATE = "{baseUrl}" + CALLBACK_BASE_URI + "/{registrationId}";

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain audienceOAuthSecurityFilterChain(HttpSecurity http,
            ClientRegistrationRepository clientRegistrations, OAuth2AuthorizedClientRepository authorizedClientRepository,
            SecurityContextRepository securityContextRepository, CsrfTokenRepository csrfTokenRepository,
            SecurityErrorResponseHandler securityErrorResponseHandler, AudienceOAuthLoginHandler loginHandler) {
        return http
                .securityMatcher(AUTHORIZATION_BASE_URI + "/*", CALLBACK_BASE_URI + "/*")
                // 시작·콜백은 로그인 없이 연다. 모르는 제공자의 시작 요청은 그대로 흘러가 404가 된다
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .securityContext(context -> context
                        .securityContextRepository(new ReadOnlySecurityContextRepository(securityContextRepository)))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(securityErrorResponseHandler)
                        .accessDeniedHandler(securityErrorResponseHandler))
                .requestCache(RequestCacheConfigurer::disable)
                .logout(LogoutConfigurer::disable)
                .oauth2Login(oauth -> oauth
                        .clientRegistrationRepository(clientRegistrations)
                        .authorizedClientRepository(authorizedClientRepository)
                        .authorizationEndpoint(authorization -> authorization
                                .baseUri(AUTHORIZATION_BASE_URI)
                                .authorizationRequestResolver(new AudienceOAuthAuthorizationRequestResolver(clientRegistrations)))
                        .redirectionEndpoint(redirection -> redirection.baseUri(CALLBACK_BASE_URI + "/*"))
                        .loginProcessingUrl(CALLBACK_BASE_URI + "/*")
                        .successHandler(loginHandler)
                        .failureHandler(loginHandler))
                .build();
    }

    /**
     * 세션의 로그인 정보를 읽기만 하는 저장소 — 읽기는 공용 저장소에 맡기고, 저장은 하지 않는다. 이 묶음의 필터가 제공자 인증 정보를 세션에
     * 저장하지 못하게 한다.
     */
    static final class ReadOnlySecurityContextRepository implements SecurityContextRepository {

        private static final Logger log = LoggerFactory.getLogger(ReadOnlySecurityContextRepository.class);

        private final SecurityContextRepository sessionRepository;

        ReadOnlySecurityContextRepository(SecurityContextRepository sessionRepository) {
            this.sessionRepository = sessionRepository;
        }

        @Override
        public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
            return sessionRepository.loadDeferredContext(request);
        }

        @Override
        @Deprecated
        public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
            return loadDeferredContext(requestResponseHolder.getRequest()).get();
        }

        @Override
        public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
            log.debug("카카오·구글 로그인 필터는 세션에 로그인 정보를 저장하지 않습니다");
        }

        @Override
        public boolean containsContext(HttpServletRequest request) {
            return sessionRepository.containsContext(request);
        }
    }
}
