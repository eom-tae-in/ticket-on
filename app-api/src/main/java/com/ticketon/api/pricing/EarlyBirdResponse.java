package com.ticketon.api.pricing;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.pricing.port.in.EarlyBirdResult;
import com.ticketon.common.KoreanTime;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 조기예매 한 줄 — 위저드 4단계의 조기예매 목록. 수정 요청의 칸 이름과 같다(할인 · 기간 · 수량 · 적용 범위). 주최자가 보는 값이라 사용 수와 상태를
 * 담는다 — 관객에게는 남은 수량 대신 임박 단계만 보인다(docs/05 "임박 표시").
 *
 * @param earlyBirdId              조기예매 id
 * @param discount                 할인
 * @param period                   기간
 * @param quantity                 수량 — 기간만 있으면 null
 * @param claimedCount             사용 수
 * @param status                   지금 상태
 * @param singleSessionExcluded    1일권을 껐는지
 * @param excludedZoneIds          끈 구역
 * @param excludedBundleProductIds 끈 묶음권 상품
 * @param overlappingEarlyBirdIds  기간이 겹치는 다른 조기예매 — 경고
 */
@Schema(description = "조기예매")
public record EarlyBirdResponse(
        @Schema(description = "조기예매 id", example = "5", requiredMode = REQUIRED)
        long earlyBirdId,

        @Schema(description = "할인", requiredMode = REQUIRED)
        DiscountResponse discount,

        @Schema(description = "기간 — 한국 시각(+09:00)", requiredMode = REQUIRED)
        PeriodResponse period,

        @Schema(description = "수량 — 기간만 있는 조기예매면 null", example = "30", requiredMode = NOT_REQUIRED, nullable = true)
        Integer quantity,

        @Schema(description = "사용 수 — 몫을 잡은 티켓 수. 선점하는 순간 늘고 줄지 않는다", example = "12", requiredMode = REQUIRED)
        int claimedCount,

        @Schema(description = "지금 상태 — 시작 전 / 진행 중 / 소진 / 기간 종료. 소진 · 기간 종료면 바꿀 수 없다", requiredMode = REQUIRED)
        EarlyBirdStatusCode status,

        @Schema(description = "1일권(회차 하나짜리 티켓)을 껐는지", example = "false", requiredMode = REQUIRED)
        boolean singleSessionExcluded,

        @ArraySchema(arraySchema = @Schema(description = "끈 구역 id — id 순. 없으면 빈 목록", requiredMode = REQUIRED))
        List<Long> excludedZoneIds,

        @ArraySchema(arraySchema = @Schema(description = "끈 묶음권 상품 id — id 순. 없으면 빈 목록", requiredMode = REQUIRED))
        List<Long> excludedBundleProductIds,

        @ArraySchema(arraySchema = @Schema(description = "기간이 겹치는 다른 조기예매 id — 저장할 때의 경고(막지 않는다). 적용 범위와 상관없이 "
                + "기간만 본다. 없으면 빈 목록", requiredMode = REQUIRED))
        List<Long> overlappingEarlyBirdIds) {

    static EarlyBirdResponse from(EarlyBirdResult result) {
        return new EarlyBirdResponse(result.earlyBirdId(), DiscountResponse.from(result.discount()),
                new PeriodResponse(korean(result.startsAt()), korean(result.endsAt())), result.quantity(), result.claimedCount(),
                EarlyBirdStatusCode.from(result.status()), result.singleSessionExcluded(), result.excludedZoneIds(),
                result.excludedBundleProductIds(), result.overlappingEarlyBirdIds());
    }

    static OffsetDateTime korean(Instant instant) {
        return instant.atZone(KoreanTime.ZONE).toOffsetDateTime();
    }

    /**
     * 할인 기간 — 시작 순간부터 끝 순간 직전까지.
     *
     * @param startsAt 시작 순간
     * @param endsAt   끝 순간 — 이 순간에는 이미 끝났다
     */
    @Schema(description = "할인 기간 — 시작 순간부터 끝 순간 직전까지")
    public record PeriodResponse(
            @Schema(description = "시작 순간", example = "2026-04-01T00:00:00+09:00", requiredMode = REQUIRED)
            OffsetDateTime startsAt,

            @Schema(description = "끝 순간 — 이 순간에는 이미 끝났다", example = "2026-04-21T00:00:00+09:00", requiredMode = REQUIRED)
            OffsetDateTime endsAt) {
    }
}
