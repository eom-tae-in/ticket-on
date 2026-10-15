package com.ticketon.api.organizer;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.organizer.port.in.GetMyAffiliationsQuery;
import com.ticketon.application.organizer.port.in.GetMyAffiliationsUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 소속 목록 — 주최자 전환. 운영진 영역(/api/organizer)이라 운영진으로 로그인해야 열린다(api/security). 고른 주최자는 서버에 기억하지 않고,
 * 주최 관리 요청이 경로에 주최자 id를 담는다({@code /api/organizer/organizers/{organizerId}/**}).
 */
@RestController
@RequestMapping("/api/organizer/affiliations")
@Tag(name = "주최자 전환", description = "내 소속 목록 — 여러 주최자에 소속되면 지금 일할 주최자를 고른다 (docs/01 \"운영자 초대\", "
        + "docs/13 \"3. 주최 관리\")")
public class MyAffiliationController {

    private final GetMyAffiliationsUseCase getMyAffiliationsUseCase;

    public MyAffiliationController(GetMyAffiliationsUseCase getMyAffiliationsUseCase) {
        this.getMyAffiliationsUseCase = getMyAffiliationsUseCase;
    }

    @Operation(summary = "내 소속 목록", description = "로그인한 운영진이 지금 소속된 주최자를 주최자명 · 역할 · 주최자 상태 · 소속 일시와 함께 소속을 "
            + "시작한 순서로 돌려준다. 끝난 소속은 담지 않는다. 로그인하지 않았으면 401, 운영진이 아닌 계정이면 403이다.")
    @GetMapping
    public MyAffiliationsResponse myAffiliations(@CurrentAccount AccountRef account) {
        return MyAffiliationsResponse.from(getMyAffiliationsUseCase.getMyAffiliations(new GetMyAffiliationsQuery(account)));
    }
}
