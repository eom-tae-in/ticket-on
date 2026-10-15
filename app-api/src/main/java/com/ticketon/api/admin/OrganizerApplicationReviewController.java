package com.ticketon.api.admin;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.admin.port.in.ApproveOrganizerApplicationCommand;
import com.ticketon.application.admin.port.in.ApproveOrganizerApplicationUseCase;
import com.ticketon.application.admin.port.in.GetOrganizerApplicationReviewQuery;
import com.ticketon.application.admin.port.in.GetOrganizerApplicationReviewUseCase;
import com.ticketon.application.admin.port.in.RejectOrganizerApplicationUseCase;
import com.ticketon.domain.account.AccountRef;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주최자 심사 — 관리자 영역(/api/admin)이라 관리자·최고 관리자로 로그인해야 열린다(api/security). 승인·반려는 관리자 조작 기록에 남는다.
 */
@RestController
@RequestMapping("/api/admin/organizer-applications/{applicationId}")
@Tag(name = "주최자 심사", description = "주최자 신청 심사 화면과 승인·반려 (docs/01 \"심사 화면\", \"반려\")")
public class OrganizerApplicationReviewController {

    private static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    private final GetOrganizerApplicationReviewUseCase getReviewUseCase;
    private final ApproveOrganizerApplicationUseCase approveUseCase;
    private final RejectOrganizerApplicationUseCase rejectUseCase;

    public OrganizerApplicationReviewController(GetOrganizerApplicationReviewUseCase getReviewUseCase,
            ApproveOrganizerApplicationUseCase approveUseCase, RejectOrganizerApplicationUseCase rejectUseCase) {
        this.getReviewUseCase = getReviewUseCase;
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
    }

    @Operation(summary = "심사 화면", description = "신청서 전체(담당자 연락처 · 사업자 정보 · 정산 계좌 포함), 체크리스트 세 항목, 반려 사유 "
            + "템플릿, 심사 목표일, 같은 소유자 또는 같은 단체명의 닫힌 주최자를 돌려준다. 이미 처리한 신청서도 결과와 함께 본다. "
            + "없는 신청서면 404 APPLICATION_NOT_FOUND. 로그인하지 않았으면 401, 관리자가 아닌 계정이면 403이다.")
    @GetMapping
    public OrganizerApplicationReviewResponse review(@CurrentAccount AccountRef account,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long applicationId) {
        return OrganizerApplicationReviewResponse.from(
                getReviewUseCase.getReview(new GetOrganizerApplicationReviewQuery(account, applicationId)));
    }

    @Operation(summary = "승인", description = "신청서를 승인한다. 주최자가 활동이 되고 신청한 사람이 소유자가 되며, 신청서의 이메일로 승인 메일이 "
            + "간다. 이미 승인·반려한 신청이거나 다른 관리자가 먼저 처리했으면 409 APPLICATION_ALREADY_REVIEWED, 없는 신청서면 404 "
            + "APPLICATION_NOT_FOUND. 승인은 관리자 조작 기록에 남는다.")
    @PostMapping("/approval")
    public ApproveOrganizerApplicationResponse approve(@CurrentAccount AccountRef account,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long applicationId) {
        return ApproveOrganizerApplicationResponse.from(
                approveUseCase.approve(new ApproveOrganizerApplicationCommand(account, applicationId)));
    }

    @Operation(summary = "반려", description = "사유를 붙여 신청서를 반려한다. 사유가 비면 400. 신청서의 이메일로 사유를 담은 반려 메일이 가고, "
            + "주최자는 심사 중으로 남는다(신청한 사람은 소속 없음 그대로, 다시 신청할 수 있다). 이미 승인·반려한 신청이거나 다른 관리자가 "
            + "먼저 처리했으면 409 APPLICATION_ALREADY_REVIEWED, 없는 신청서면 404 APPLICATION_NOT_FOUND. 반려는 사유와 함께 관리자 "
            + "조작 기록에 남는다.")
    @PostMapping("/rejection")
    public RejectOrganizerApplicationResponse reject(@CurrentAccount AccountRef account,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long applicationId,
            @Valid @RequestBody RejectOrganizerApplicationRequest request) {
        return RejectOrganizerApplicationResponse.from(rejectUseCase.reject(request.toCommand(account, applicationId)));
    }
}
