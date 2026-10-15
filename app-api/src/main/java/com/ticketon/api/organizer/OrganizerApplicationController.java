package com.ticketon.api.organizer;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.organizer.port.in.SubmitOrganizerApplicationUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주최자 신청 — 운영진 영역(/api/organizer)이라 운영진으로 로그인해야 열린다(api/security).
 */
@RestController
@RequestMapping("/api/organizer/applications")
@Tag(name = "주최자 신청", description = "새 주최자 신청서 제출 (docs/01 \"주최자 신청과 승인\", \"신청 항목\", \"반려\")")
public class OrganizerApplicationController {

    private final SubmitOrganizerApplicationUseCase submitOrganizerApplicationUseCase;

    public OrganizerApplicationController(SubmitOrganizerApplicationUseCase submitOrganizerApplicationUseCase) {
        this.submitOrganizerApplicationUseCase = submitOrganizerApplicationUseCase;
    }

    @Operation(summary = "새 주최자 신청서 제출", description = "로그인한 운영진이 새 주최자 신청서를 낸다. 주최자가 '심사 중'으로 생기고 "
            + "신청은 플랫폼 관리자의 심사를 기다린다. 승인되면 신청한 사람이 소유자가 된다. 반려된 뒤에는 횟수 제한 없이 다시 낼 수 있다. "
            + "사업자인데 사업자 정보가 빠지면 409 BUSINESS_INFORMATION_MISSING, 비사업자인데 사업자 정보를 보내면 409 "
            + "BUSINESS_INFORMATION_FOR_NON_BUSINESS, 심사 중인 신청이 있으면 409 APPLICATION_ALREADY_UNDER_REVIEW. "
            + "로그인하지 않았거나 세션이 끝났으면 401, 운영진이 아닌 계정이면 403이다.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizerApplicationUnderReviewResponse submit(@CurrentAccount AccountRef account,
            @Valid @RequestBody SubmitOrganizerApplicationRequest request) {
        return OrganizerApplicationUnderReviewResponse.from(
                submitOrganizerApplicationUseCase.submit(request.toCommand(account)));
    }
}
