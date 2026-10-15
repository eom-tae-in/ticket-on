package com.ticketon.api.organizer;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.organizer.port.in.GetOrganizerHomeQuery;
import com.ticketon.application.organizer.port.in.GetOrganizerHomeUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 내 주최 홈 — 주최 관리에 들어오면 처음 보는 상태. 운영진 영역(/api/organizer)이라 운영진으로 로그인해야 열린다(api/security).
 */
@RestController
@RequestMapping("/api/organizer/home")
@Tag(name = "주최 홈", description = "주최 관리 진입 상태 — 소속 없음 · 심사 중 · 주최자 전환 (docs/13 \"3. 주최 관리 — 로그인\")")
public class OrganizerHomeController {

    private final GetOrganizerHomeUseCase getOrganizerHomeUseCase;

    public OrganizerHomeController(GetOrganizerHomeUseCase getOrganizerHomeUseCase) {
        this.getOrganizerHomeUseCase = getOrganizerHomeUseCase;
    }

    @Operation(summary = "내 주최 홈 상태", description = "지금 소속된 주최자(역할), 심사 중인 신청, 가장 최근 신청이 반려됐으면 그 사유, "
            + "받은 운영자 초대, 다시 열기를 신청할 수 있는 닫힌 주최자를 돌려준다. 심사 중인 신청이 있으면 화면은 \"심사 중\" 화면만 보이고, "
            + "소속이 없으면 \"새 주최자 신청\"과 \"받은 초대\"를 보인다. 로그인하지 않았으면 401, 운영진이 아닌 계정이면 403이다.")
    @GetMapping
    public OrganizerHomeResponse getHome(@CurrentAccount AccountRef account) {
        return OrganizerHomeResponse.from(getOrganizerHomeUseCase.getHome(new GetOrganizerHomeQuery(account)));
    }
}
