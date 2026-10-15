package com.ticketon.api.account;

import com.ticketon.api.account.AudienceOAuthSession.LinkIntent;
import com.ticketon.api.account.AudienceOAuthSession.Start;
import com.ticketon.api.security.ReturnPath;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * 카카오·구글 로그인 시작 — 제공자로 보낼 인가 요청(state 포함)은 Spring 기본 리졸버가 만들고, 여기서는 시작 정보를 세션에 둔다.
 * <ul>
 *   <li>마이페이지에서 같은 제공자로 연결을 시작해 둔 의도가 있으면 연결로 시작한다. 연결 의도는 이 시작이 가져가므로 한 번만 쓰인다.</li>
 *   <li>아니면 로그인으로 시작한다. 돌아갈 경로는 시작 요청의 {@value #RETURN_PATH_PARAMETER}다 — 같은 사이트 안의 경로만 받고
 *       ({@link ReturnPath}), 없거나 받지 않는 경로면 관객 첫 화면이다. 로그인 후 보던 화면으로 돌아가게 한다(docs/06 "전체 흐름").</li>
 *   <li>모르는 제공자거나 키가 없어 등록되지 않은 제공자면 인가 요청을 만들지 않는다. 요청이 그대로 흘러가 없는 주소(404)가 된다.</li>
 * </ul>
 */
final class AudienceOAuthAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    /** 로그인 시작 요청에서 돌아갈 화면 경로를 담는 쿼리 이름. */
    static final String RETURN_PATH_PARAMETER = "returnPath";

    private static final String REGISTRATION_ID_VARIABLE = "registrationId";

    private final ClientRegistrationRepository clientRegistrations;
    private final DefaultOAuth2AuthorizationRequestResolver defaultResolver;
    private final PathPatternRequestMatcher startRequestMatcher;

    AudienceOAuthAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrations) {
        this.clientRegistrations = clientRegistrations;
        this.defaultResolver = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrations,
                AudienceOAuthSecurityConfiguration.AUTHORIZATION_BASE_URI);
        this.startRequestMatcher = PathPatternRequestMatcher.withDefaults()
                .matcher(AudienceOAuthSecurityConfiguration.AUTHORIZATION_BASE_URI + "/{" + REGISTRATION_ID_VARIABLE + "}");
    }

    @Override
    public @Nullable OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        Optional<OAuthProviderCode> provider = registeredProviderOf(request);
        if (provider.isEmpty()) {
            return null;
        }
        OAuth2AuthorizationRequest authorizationRequest = defaultResolver.resolve(request);
        if (authorizationRequest != null) {
            AudienceOAuthSession.start(request, startOf(request, provider.get()));
        }
        return authorizationRequest;
    }

    /** 다른 기능이 제공자 인가를 다시 받으라고 요청하는 흐름(제공자 API 호출)은 쓰지 않는다. 로그인 시작 주소로만 시작한다. */
    @Override
    public @Nullable OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        return null;
    }

    private Optional<OAuthProviderCode> registeredProviderOf(HttpServletRequest request) {
        if (!startRequestMatcher.matches(request)) {
            return Optional.empty();
        }
        String registrationId = startRequestMatcher.matcher(request).getVariables().get(REGISTRATION_ID_VARIABLE);
        return OAuthProviderCode.fromRegistrationId(registrationId)
                .filter(code -> clientRegistrations.findByRegistrationId(code.registrationId()) != null);
    }

    private static Start startOf(HttpServletRequest request, OAuthProviderCode provider) {
        Optional<LinkIntent> linkIntent = AudienceOAuthSession.takeLinkIntent(request);
        if (linkIntent.isPresent() && linkIntent.get().provider() == provider) {
            return Start.link(linkIntent.get());
        }
        String returnPath = ReturnPath.sameSiteOf(request.getParameter(RETURN_PATH_PARAMETER))
                .orElse(AudienceLoginController.AUDIENCE_HOME_PATH);
        return Start.logIn(provider, returnPath);
    }
}
