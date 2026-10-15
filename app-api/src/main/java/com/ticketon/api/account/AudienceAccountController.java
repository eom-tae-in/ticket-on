package com.ticketon.api.account;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.account.port.in.GetMyAudienceAccountQuery;
import com.ticketon.application.account.port.in.GetMyAudienceAccountUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 관객 계정 — 관객 영역(/api/audience)이라 관객으로 로그인해야 열린다(api/security).
 */
@RestController
@RequestMapping("/api/audience/account")
@Tag(name = "관객 계정", description = "마이페이지 계정 정보 (docs/13 \"마이페이지\")")
public class AudienceAccountController {

    private final GetMyAudienceAccountUseCase getMyAudienceAccountUseCase;

    public AudienceAccountController(GetMyAudienceAccountUseCase getMyAudienceAccountUseCase) {
        this.getMyAudienceAccountUseCase = getMyAudienceAccountUseCase;
    }

    @Operation(summary = "내 계정 조회", description = "로그인한 관객의 이름·이메일·전화번호·이용 정지 여부·연결된 카카오·구글. 이용 정지 중에도 조회된다. "
            + "로그인하지 않았거나 세션이 끝났으면 401, 관객이 아닌 계정이면 403이다.")
    @GetMapping
    public MyAudienceAccountResponse getMyAccount(@CurrentAccount AccountRef account) {
        return MyAudienceAccountResponse.from(getMyAudienceAccountUseCase.getMyAccount(new GetMyAudienceAccountQuery(account)));
    }
}
