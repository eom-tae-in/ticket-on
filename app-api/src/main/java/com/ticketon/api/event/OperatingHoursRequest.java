package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.domain.event.OperatingHours;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

/**
 * 운영 시간 — 종일형 회차의 시간(docs/02 "회차의 시간 정보" — 운영 시작~종료 필수, 마지막 입장 선택). 행사 단위의 기본 운영 시간과 날짜별로 덮어쓰는
 * 운영 시간이 같은 모양이다. 시각은 "HH:mm"(분 단위 — 초가 0이 아니면 400, {@link WholeMinute}), 차례는 {@link OperatingHoursOrder}가 본다.
 *
 * @param openTime      운영 시작
 * @param closeTime     운영 종료
 * @param lastEntryTime 마지막 입장 — 비우면 운영 종료와 같게 처리한다
 */
@Schema(description = "운영 시간 (종일형)")
@OperatingHoursOrder
public record OperatingHoursRequest(
        @Schema(description = "운영 시작 — 그 날짜의 한국 시각, 분 단위", example = "10:00", type = "string", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9](:00)?$", requiredMode = REQUIRED)
        @NotNull(message = "운영 시작 시각을 입력해 주세요.")
        @WholeMinute
        LocalTime openTime,

        @Schema(description = "운영 종료 — 운영 시작보다 늦게. 자정을 넘기는 운영은 아직 받지 않는다(확인 필요)", example = "18:00",
                type = "string", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9](:00)?$", requiredMode = REQUIRED)
        @NotNull(message = "운영 종료 시각을 입력해 주세요.")
        @WholeMinute
        LocalTime closeTime,

        @Schema(description = "마지막 입장 — 선택. 운영 시작보다 늦고 운영 종료보다 늦지 않게. 비우면 운영 종료와 같게 처리한다", example = "17:00",
                type = "string", pattern = "^([01][0-9]|2[0-3]):[0-5][0-9](:00)?$", requiredMode = NOT_REQUIRED, nullable = true)
        @WholeMinute
        LocalTime lastEntryTime) {

    OperatingHours toOperatingHours() {
        return OperatingHours.of(openTime, closeTime, lastEntryTime);
    }
}
