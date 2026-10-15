package com.ticketon.api.admin;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.admin.port.in.ApproveOwnershipClaimCommand;
import com.ticketon.application.admin.port.in.ApproveOwnershipClaimUseCase;
import com.ticketon.application.admin.port.in.GetOwnershipClaimReviewQuery;
import com.ticketon.application.admin.port.in.GetOwnershipClaimReviewUseCase;
import com.ticketon.application.admin.port.in.RejectOwnershipClaimUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 소유자 이전 신청 심사 — 관리자 영역(/api/admin)이라 관리자·최고 관리자로 로그인해야 열린다(api/security). 승인·반려는 관리자 조작 기록에
 * 남는다.
 */
@RestController
@RequestMapping("/api/admin/ownership-claims/{ownershipClaimId}")
@Tag(name = "소유자 이전 신청 심사", description = "처리 목록의 '소유자 이전 신청' 심사 화면과 승인·반려 (docs/01 \"소유자와 연락이 끊긴 경우\", "
        + "docs/11 \"대표가 바뀔 때\")")
public class OwnershipClaimReviewController {

    private static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    private final GetOwnershipClaimReviewUseCase getReviewUseCase;
    private final ApproveOwnershipClaimUseCase approveUseCase;
    private final RejectOwnershipClaimUseCase rejectUseCase;

    public OwnershipClaimReviewController(GetOwnershipClaimReviewUseCase getReviewUseCase,
            ApproveOwnershipClaimUseCase approveUseCase, RejectOwnershipClaimUseCase rejectUseCase) {
        this.getReviewUseCase = getReviewUseCase;
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
    }

    @Operation(summary = "심사 화면", description = "소유자 이전 신청(처리 목록의 OWNERSHIP_CLAIM 항목, 대상 id)을 신청 사유 · 신청한 운영자 · "
            + "신청 때의 소유자 · 지금 소유자, 주최자 신청 때 받은 연락처 · SNS와 함께 돌려준다. 이미 처리한 신청도 결과와 함께 본다. 없는 신청이면 "
            + "404 OWNERSHIP_CLAIM_NOT_FOUND. 로그인하지 않았으면 401, 관리자가 아닌 계정이면 403이다.")
    @GetMapping
    public OwnershipClaimReviewResponse review(@CurrentAccount AccountRef account,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long ownershipClaimId) {
        return OwnershipClaimReviewResponse.from(
                getReviewUseCase.getReview(new GetOwnershipClaimReviewQuery(account, ownershipClaimId)));
    }

    @Operation(summary = "승인", description = "신청을 승인해 소유자를 바꾼다. 신청한 운영자가 소유자가 되고 신청 때의 소유자는 운영자로 남는다. "
            + "바뀐 뒤의 운영진 전원에게 '소유자 변경' 메일([주최])이 가고, 소유자 변경 이력에 '신청 승인'으로 남는다(처리 목록 '소유자 변경'에는 "
            + "쌓이지 않는다). 이미 승인·반려했거나 다른 관리자가 먼저 처리했으면 409 OWNERSHIP_CLAIM_ALREADY_REVIEWED, 신청 뒤 소유자가 이미 "
            + "바뀌었으면 409 OWNERSHIP_ALREADY_TRANSFERRED, 신청한 사람이 운영자가 아니면(나갔거나 탈퇴했으면) 409 CLAIMANT_NOT_OPERATOR, "
            + "주최자가 활동 중이 아니면 409 ORGANIZER_NOT_ACTIVE — 이때는 반려한다. 없는 신청이면 404 OWNERSHIP_CLAIM_NOT_FOUND. 관리자 "
            + "조작 기록에 남는다.")
    @PostMapping("/approval")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@CurrentAccount AccountRef account,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long ownershipClaimId) {
        approveUseCase.approve(new ApproveOwnershipClaimCommand(account, ownershipClaimId));
    }

    @Operation(summary = "반려", description = "사유를 붙여 신청을 반려한다. 사유가 비면 400. 사유를 담은 반려 메일([주최])이 운영진 전원(신청한 "
            + "운영자 포함)에게 가고, 소유자는 그대로다. 이미 승인·반려했거나 다른 관리자가 먼저 처리했으면 409 OWNERSHIP_CLAIM_ALREADY_REVIEWED, "
            + "없는 신청이면 404 OWNERSHIP_CLAIM_NOT_FOUND. 사유와 함께 관리자 조작 기록에 남는다.")
    @PostMapping("/rejection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@CurrentAccount AccountRef account,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long ownershipClaimId,
            @Valid @RequestBody RejectOwnershipClaimRequest request) {
        rejectUseCase.reject(request.toCommand(account, ownershipClaimId));
    }
}
