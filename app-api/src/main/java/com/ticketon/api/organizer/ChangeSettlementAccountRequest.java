package com.ticketon.api.organizer;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.RequestOrganizerChangeCommand;
import com.ticketon.domain.organizer.RequestedOrganizerChange;
import com.ticketon.domain.organizer.SettlementAccount;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 정산 계좌 변경 요청 — 새 정산 계좌의 은행 · 계좌번호 · 예금주를 모두 적는다(docs/11 "정산 계좌 변경"). 형식은 은행마다 달라 비었는지만 도메인
 * {@link SettlementAccount}와 같은 규칙(앞뒤 공백을 {@link String#strip()}으로 걷어 글자가 남는지)으로 본다(400, {@link FilledText}).
 * 민감한 값이라 {@link #toString}에 싣지 않는다.
 *
 * @param bankName      은행
 * @param accountNumber 계좌번호 — 적은 그대로 둔다
 * @param accountHolder 예금주
 */
@Schema(description = "정산 계좌 변경 요청 — 관리자 확인 뒤 적용된다")
public record ChangeSettlementAccountRequest(
        @Schema(description = "은행", example = "우리은행", requiredMode = REQUIRED)
        @FilledText(message = "은행을 입력해 주세요.")
        String bankName,

        @Schema(description = "계좌번호 — 적은 그대로 둔다", example = "1002-345-678901", requiredMode = REQUIRED)
        @FilledText(message = "계좌번호를 입력해 주세요.")
        String accountNumber,

        @Schema(description = "예금주", example = "박정훈", requiredMode = REQUIRED)
        @FilledText(message = "예금주를 입력해 주세요.")
        String accountHolder) {

    RequestOrganizerChangeCommand toCommand(ActingStaff actingStaff) {
        return new RequestOrganizerChangeCommand(actingStaff, new RequestedOrganizerChange.NewSettlementAccount(
                new SettlementAccount(bankName, accountNumber, accountHolder)));
    }

    @Override
    public String toString() {
        return "ChangeSettlementAccountRequest[****]";
    }
}
