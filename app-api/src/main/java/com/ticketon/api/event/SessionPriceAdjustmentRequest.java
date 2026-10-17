package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.ChangeSessionPriceAdjustmentCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.domain.event.SessionPriceAdjustment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 회차 가격 조정 요청 — 조정 없음 / 금액 ± / 퍼센트 ±(docs/05 "회차별 가격 조정" — "3/15(토) +10,000원", "3/12(수, 프리뷰) -20%"). 종류에 맞는 칸
 * 하나만 보낸다({@link PriceAdjustmentShape} — 400). 금액 0원 · 퍼센트 0%는 조정 없음으로 저장된다.
 *
 * @param kind    종류
 * @param amount  금액 조정(원) — 금액일 때만
 * @param percent 퍼센트 조정 — 퍼센트일 때만
 */
@Schema(description = "회차 가격 조정")
@PriceAdjustmentShape
public record SessionPriceAdjustmentRequest(
        @Schema(description = "종류 — 조정 없음 / 금액 / 퍼센트", requiredMode = REQUIRED)
        @NotNull(message = "가격 조정 종류를 골라 주세요.")
        PriceAdjustmentKindCode kind,

        @Schema(description = "금액 조정(원) — 금액일 때만. 빼려면 음수. 0이면 조정 없음", example = "10000", requiredMode = NOT_REQUIRED,
                nullable = true)
        Long amount,

        @Schema(description = "퍼센트 조정 — 퍼센트일 때만. 빼려면 음수, -100보다 크게. 0이면 조정 없음. 조정한 가격은 100원 단위로 내린다",
                example = "-20", requiredMode = NOT_REQUIRED, nullable = true)
        @PriceAdjustmentPercentRule
        Integer percent) {

    ChangeSessionPriceAdjustmentCommand toCommand(ActingStaff actingStaff, long eventId, long sessionId) {
        return new ChangeSessionPriceAdjustmentCommand(actingStaff, eventId, sessionId, toPriceAdjustment());
    }

    private SessionPriceAdjustment toPriceAdjustment() {
        return switch (kind) {
            case NONE -> SessionPriceAdjustment.none();
            case AMOUNT -> SessionPriceAdjustment.amount(amount);
            case PERCENT -> SessionPriceAdjustment.percent(percent);
        };
    }
}
