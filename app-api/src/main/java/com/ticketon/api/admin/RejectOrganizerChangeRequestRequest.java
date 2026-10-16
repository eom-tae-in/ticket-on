package com.ticketon.api.admin;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.application.admin.port.in.RejectOrganizerChangeRequestCommand;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.organizer.RejectionReason;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 정산 계좌 · 사업자 정보 변경 반려 요청 — 사유는 필수다. 소유자에게 메일로 그대로 전달된다. 문서에 정한 사유 템플릿이 없어 직접 입력한다.
 * 비었는지는 도메인 {@link RejectionReason}과 같이 앞뒤 공백을 {@link String#strip()}으로 걷어 본다({@link FilledText}).
 *
 * @param reason 반려 사유
 */
@Schema(description = "정산 계좌 · 사업자 정보 변경 반려 요청")
public record RejectOrganizerChangeRequestRequest(
        @Schema(description = "반려 사유 — 소유자에게 메일로 그대로 전달된다", example = "예금주가 대표자 이름과 다릅니다. 통장 사본을 보내 주세요.",
                requiredMode = REQUIRED)
        @FilledText(message = "반려 사유를 입력해 주세요.")
        String reason) {

    RejectOrganizerChangeRequestCommand toCommand(AccountRef admin, long changeRequestId) {
        return new RejectOrganizerChangeRequestCommand(admin, changeRequestId, RejectionReason.of(reason));
    }
}
