package com.ticketon.api.pricing;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.domain.pricing.DiscountPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * 조기예매 기간 · 쿠폰 유효 기간 — 시작 순간부터 끝 순간 직전까지, 둘 다 필수(docs/99 C "할인 기간 (조기예매·쿠폰)"). 끝이 시작보다 뒤인지는
 * 도메인과 같은 규칙으로 본다(400 — {@link PeriodOrder}). "4/20까지"처럼 날짜까지 받으려면 화면이 그다음 날 0시를 끝으로 보낸다.
 *
 * @param startsAt 시작 순간 — 이 순간부터 들어간다
 * @param endsAt   끝 순간 — 이 순간에는 이미 끝났다
 */
@Schema(description = "할인 기간 — 시작 순간부터 끝 순간 직전까지")
@PeriodOrder
public record DiscountPeriodRequest(
        @Schema(description = "시작 순간 — 이 순간부터 들어간다", example = "2026-04-01T00:00:00+09:00", requiredMode = REQUIRED)
        @NotNull(message = "시작 시각을 입력해 주세요.")
        OffsetDateTime startsAt,

        @Schema(description = "끝 순간 — 이 순간에는 이미 끝났다. 시작보다 뒤", example = "2026-04-21T00:00:00+09:00", requiredMode = REQUIRED)
        @NotNull(message = "끝 시각을 입력해 주세요.")
        OffsetDateTime endsAt) {

    DiscountPeriod toPeriod() {
        return DiscountPeriod.of(startsAt.toInstant(), endsAt.toInstant());
    }
}
