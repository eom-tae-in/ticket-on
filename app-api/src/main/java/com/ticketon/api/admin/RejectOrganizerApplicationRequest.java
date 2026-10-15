package com.ticketon.api.admin;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.application.admin.port.in.RejectOrganizerApplicationCommand;
import com.ticketon.domain.account.AccountRef;
import com.ticketon.domain.organizer.RejectionReason;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 주최자 신청 반려 요청 — 사유는 필수다(docs/01 "반려 — 사유를 반드시 남기고 전달합니다").
 * <p>
 * 템플릿("연락이 닿지 않음", "정보가 부족함", "서비스 성격과 맞지 않음" — 심사 화면의 rejectionReasonTemplates)을 고르면 그 글을, 직접 입력하면
 * 입력한 글을 보낸다. 어느 쪽이든 신청한 사람에게 전달되는 것은 이 글이다. 비었는지는 도메인 {@link RejectionReason}과 같이 앞뒤 공백을
 * {@link String#strip()}으로 걷어 본다({@link FilledText}).
 *
 * @param reason 반려 사유
 */
@Schema(description = "주최자 신청 반려 요청")
public record RejectOrganizerApplicationRequest(
        @Schema(description = "반려 사유 — 템플릿 글 그대로 또는 직접 입력한 글. 신청한 사람에게 메일로 그대로 전달된다",
                example = "연락이 닿지 않음", requiredMode = REQUIRED)
        @FilledText(message = "반려 사유를 입력해 주세요.")
        String reason) {

    RejectOrganizerApplicationCommand toCommand(AccountRef reviewer, long applicationId) {
        return new RejectOrganizerApplicationCommand(reviewer, applicationId, RejectionReason.of(reason));
    }
}
