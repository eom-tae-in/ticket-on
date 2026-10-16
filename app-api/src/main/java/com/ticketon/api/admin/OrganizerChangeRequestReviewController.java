package com.ticketon.api.admin;

import com.ticketon.api.security.CurrentAccount;
import com.ticketon.application.admin.port.in.ApproveOrganizerChangeRequestCommand;
import com.ticketon.application.admin.port.in.ApproveOrganizerChangeRequestUseCase;
import com.ticketon.application.admin.port.in.GetOrganizerChangeRequestReviewQuery;
import com.ticketon.application.admin.port.in.GetOrganizerChangeRequestReviewUseCase;
import com.ticketon.application.admin.port.in.RejectOrganizerChangeRequestUseCase;
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
 * 정산 계좌 · 사업자 정보 변경 확인 — 관리자 영역(/api/admin)이라 관리자·최고 관리자로 로그인해야 열린다(api/security). 승인·반려는 관리자 조작
 * 기록에 남는다.
 */
@RestController
@RequestMapping("/api/admin/organizer-change-requests/{changeRequestId}")
@Tag(name = "정산 계좌 · 사업자 정보 변경 확인", description = "처리 목록의 '정산 계좌 · 사업자 정보 변경 확인' 화면과 승인·반려 (docs/01 "
        + "\"가로채기를 막는 장치\", \"판매자 정보\", docs/11 \"정산 계좌 변경\")")
public class OrganizerChangeRequestReviewController {

    private static final String INVALID_ID_MESSAGE = "형식이 올바르지 않습니다.";

    private final GetOrganizerChangeRequestReviewUseCase getReviewUseCase;
    private final ApproveOrganizerChangeRequestUseCase approveUseCase;
    private final RejectOrganizerChangeRequestUseCase rejectUseCase;

    public OrganizerChangeRequestReviewController(GetOrganizerChangeRequestReviewUseCase getReviewUseCase,
            ApproveOrganizerChangeRequestUseCase approveUseCase, RejectOrganizerChangeRequestUseCase rejectUseCase) {
        this.getReviewUseCase = getReviewUseCase;
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
    }

    @Operation(summary = "확인 화면", description = "변경 요청(처리 목록의 ORGANIZER_CHANGE_REQUEST 항목, 대상 id)을 요청한 값 · 지금 값 · "
            + "요청한 소유자 · 지금 소유자와 함께 돌려준다. 종류에 맞는 값만 채우고, 관리자 화면이라 계좌번호를 가리지 않는다. 이미 처리한 요청도 "
            + "결과와 함께 본다. 없는 요청이면 404 ORGANIZER_CHANGE_REQUEST_NOT_FOUND. 로그인하지 않았으면 401, 관리자가 아닌 계정이면 403이다.")
    @GetMapping
    public OrganizerChangeRequestReviewResponse review(@CurrentAccount AccountRef account,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long changeRequestId) {
        return OrganizerChangeRequestReviewResponse.from(
                getReviewUseCase.getReview(new GetOrganizerChangeRequestReviewQuery(account, changeRequestId)));
    }

    @Operation(summary = "승인", description = "요청한 새 값을 주최자에 적용한다 — 정산 계좌면 이제부터 그 계좌로 정산되고, 사업자 정보면 판매자 "
            + "정보에 바뀐 값이 나간다. 소유자에게 결과 메일([주최])이 간다. 이미 승인·반려했거나 다른 관리자가 먼저 처리했으면 409 "
            + "ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED, 요청한 사람이 지금 소유자가 아니면(그 사이 소유자를 넘겼으면) 409 "
            + "CHANGE_REQUESTER_NOT_OWNER, 주최자가 활동 중이 아니면 409 ORGANIZER_NOT_ACTIVE — 이때는 반려한다. 없는 요청이면 404 "
            + "ORGANIZER_CHANGE_REQUEST_NOT_FOUND. 관리자 조작 기록에 남는다(값은 남기지 않는다).")
    @PostMapping("/approval")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@CurrentAccount AccountRef account,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long changeRequestId) {
        approveUseCase.approve(new ApproveOrganizerChangeRequestCommand(account, changeRequestId));
    }

    @Operation(summary = "반려", description = "사유를 붙여 요청을 반려한다. 사유가 비면 400. 사유를 담은 결과 메일([주최])이 소유자에게 가고, "
            + "지금 값은 그대로다. 이미 승인·반려했거나 다른 관리자가 먼저 처리했으면 409 ORGANIZER_CHANGE_REQUEST_ALREADY_REVIEWED, 없는 "
            + "요청이면 404 ORGANIZER_CHANGE_REQUEST_NOT_FOUND. 사유와 함께 관리자 조작 기록에 남는다.")
    @PostMapping("/rejection")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@CurrentAccount AccountRef account,
            @PathVariable @Positive(message = INVALID_ID_MESSAGE) long changeRequestId,
            @Valid @RequestBody RejectOrganizerChangeRequestRequest request) {
        rejectUseCase.reject(request.toCommand(account, changeRequestId));
    }
}
