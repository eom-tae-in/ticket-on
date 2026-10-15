package com.ticketon.api.account;

import com.ticketon.api.account.AudienceOAuthSession.LinkIntent;
import com.ticketon.api.security.CurrentAccount;
import com.ticketon.api.security.ReturnPath;
import com.ticketon.application.account.port.in.UnlinkOAuthCommand;
import com.ticketon.application.account.port.in.UnlinkOAuthUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 카카오·구글 연동 — 관객 영역(/api/audience)이라 관객으로 로그인해야 열린다(api/security).
 * <p>
 * <b>연동은 이미 로그인한 상태에서 사용자가 직접 한다</b>(docs/01 "계정 연동"). 이메일이 같다고 저절로 묶지 않는다.
 * 연결은 제공자에서 그 계정으로 로그인해 확인해야 하므로 두 단계다: 연결 시작으로 의도를 세션에 두고, 브라우저를 로그인 시작 주소로 옮기면
 * 제공자를 거쳐 돌아온 뒤 연결된다(결과는 GET /api/auth/audience/oauth2/result의 LINKED). 해제는 바로 한다.
 */
@RestController
@RequestMapping("/api/audience/account/oauth-links")
@Tag(name = "카카오·구글 연동", description = "마이페이지 계정 정보 · OAuth 연동 — 로그인한 관객이 직접 연결·해제한다 (docs/01 \"계정 연동\", "
        + "docs/13 \"계정 정보 · OAuth 연동\")")
public class AudienceOAuthLinkController {

    private final UnlinkOAuthUseCase unlinkOAuthUseCase;

    public AudienceOAuthLinkController(UnlinkOAuthUseCase unlinkOAuthUseCase) {
        this.unlinkOAuthUseCase = unlinkOAuthUseCase;
    }

    @Operation(summary = "연결 시작", description = "지금 로그인한 관객 계정에 그 제공자를 연결하겠다는 의도를 세션에 두고, 브라우저를 옮길 로그인 시작 주소를 "
            + "돌려준다. 제공자 로그인·동의를 거쳐 화면의 /oauth/result로 돌아오고, 결과 API가 LINKED(또는 FAILED와 까닭)를 돌려준다. "
            + "다른 관객 계정에 연결된 제공자 계정, 이미 같은 제공자의 다른 계정이 연결된 경우는 결과의 FAILED로 알린다. 모르는 제공자 이름이면 400.")
    @PostMapping("/{provider}")
    public OAuthLinkStartResponse startLink(@CurrentAccount AccountRef account, @PathVariable OAuthProviderCode provider,
            @Valid @RequestBody(required = false) @Nullable OAuthLinkStartRequest request, HttpServletRequest httpRequest) {
        String returnPath = ReturnPath.sameSiteOf(request == null ? null : request.returnPath())
                .orElse(AudienceLoginController.AUDIENCE_HOME_PATH);
        AudienceOAuthSession.saveLinkIntent(httpRequest, LinkIntent.of(provider, account, returnPath));
        return new OAuthLinkStartResponse(
                AudienceOAuthSecurityConfiguration.AUTHORIZATION_BASE_URI + "/" + provider.registrationId());
    }

    @Operation(summary = "연결 해제", description = "그 제공자의 연결을 해제한다. 이용 정지 중에도 된다. 해제하면 로그인 수단(비밀번호·연결한 계정)이 하나도 "
            + "남지 않으면 409 LAST_LOGIN_METHOD — 비밀번호 찾기로 비밀번호를 정하거나 다른 계정을 연결한 뒤 해제한다. 연결이 없으면 409 "
            + "OAUTH_PROVIDER_NOT_LINKED. 모르는 제공자 이름이면 400.")
    @DeleteMapping("/{provider}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@CurrentAccount AccountRef account, @PathVariable OAuthProviderCode provider) {
        unlinkOAuthUseCase.unlink(new UnlinkOAuthCommand(account, provider.toProvider()));
    }
}
