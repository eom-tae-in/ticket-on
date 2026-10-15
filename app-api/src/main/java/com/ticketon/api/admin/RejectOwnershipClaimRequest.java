package com.ticketon.api.admin;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.application.admin.port.in.RejectOwnershipClaimCommand;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.organizer.RejectionReason;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 소유자 이전 신청 반려 요청 — 사유는 필수다. 운영진 전원에게 메일로 그대로 전달된다. 소유자 이전 신청에는 문서에 정한 사유 템플릿이 없어 직접
 * 입력한다. 비었는지는 도메인 {@link RejectionReason}과 같이 앞뒤 공백을 {@link String#strip()}으로 걷어 본다({@link FilledText}).
 *
 * @param reason 반려 사유
 */
@Schema(description = "소유자 이전 신청 반려 요청")
public record RejectOwnershipClaimRequest(
        @Schema(description = "반려 사유 — 운영진 전원(신청한 운영자 포함)에게 메일로 그대로 전달된다", example = "소유자와 통화해 직접 넘기기로 함",
                requiredMode = REQUIRED)
        @FilledText(message = "반려 사유를 입력해 주세요.")
        String reason) {

    RejectOwnershipClaimCommand toCommand(AccountRef admin, long ownershipClaimId) {
        return new RejectOwnershipClaimCommand(admin, ownershipClaimId, RejectionReason.of(reason));
    }
}
