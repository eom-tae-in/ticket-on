package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.SessionPriceAdjustmentResult;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회차 가격 조정 — 종류에 맞는 칸 하나만 찬다. 수정 요청은 이 칸 이름으로 보낸다.
 *
 * @param kind    종류
 * @param amount  금액 조정(원) — 금액일 때만
 * @param percent 퍼센트 조정 — 퍼센트일 때만
 */
@Schema(description = "회차 가격 조정")
public record SessionPriceAdjustmentResponse(
        @Schema(description = "종류 — 조정 없음 / 금액 / 퍼센트", requiredMode = REQUIRED)
        PriceAdjustmentKindCode kind,

        @Schema(description = "금액 조정(원) — 금액일 때만, 빼면 음수", example = "10000", requiredMode = NOT_REQUIRED, nullable = true)
        Long amount,

        @Schema(description = "퍼센트 조정 — 퍼센트일 때만, 빼면 음수", example = "-20", requiredMode = NOT_REQUIRED, nullable = true)
        Integer percent) {

    static SessionPriceAdjustmentResponse from(SessionPriceAdjustmentResult result) {
        return new SessionPriceAdjustmentResponse(PriceAdjustmentKindCode.from(result.kind()), result.amount(), result.percent());
    }
}
