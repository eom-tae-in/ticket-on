package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.application.event.port.in.AddZoneCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 구역 추가 요청 — 위저드 3단계의 "구역 추가"(docs/02 "설정 흐름"). 지정형은 블록 · 좌석 배정이 이어지고, 수량형은 정원 입력만으로 끝난다.
 * <p>
 * 칸마다 비었는지 · 길이 · 꼴 · 범위는 여기서 도메인 값 객체와 같은 규칙으로 본다(400 — {@link FilledText}, {@link ZoneNameRule},
 * {@link ZoneColorRule}, {@link ZoneBasePriceRule}, {@link ZoneCapacityRule}). 정원이 종류와 맞는지(수량형은 있고 지정형은 없다)와 이름이 다른
 * 구역 · 블록과 겹치는지는 유스케이스가 본다(409).
 *
 * @param kind      종류
 * @param name      이름
 * @param color     색
 * @param basePrice 기본가(원)
 * @param capacity  정원 — 수량형만
 */
@Schema(description = "구역 추가 (위저드 3단계)")
public record AddZoneRequest(
        @Schema(description = "종류 — 지정형(좌석을 배정) / 수량형(정원만). 만든 뒤에는 바꾸지 않는다", requiredMode = REQUIRED)
        @NotNull(message = "구역 종류를 골라 주세요.")
        ZoneKindCode kind,

        @Schema(description = "이름 — 앞뒤 공백은 걷는다. 20자까지(확인 필요). 같은 행사의 다른 구역 · 블록 이름과 겹칠 수 없다", example = "스탠딩A",
                requiredMode = REQUIRED)
        @FilledText(message = "구역 이름을 입력해 주세요.")
        @ZoneNameRule
        String name,

        @Schema(description = "색 — 좌석 선택 화면에서 구역을 가르는 #RRGGBB. 대소문자를 가리지 않고 받아 대문자로 맞춘다", example = "#30A46C",
                requiredMode = REQUIRED)
        @FilledText(message = "구역 색을 골라 주세요.")
        @ZoneColorRule
        String color,

        @Schema(description = "기본가(원) — 1 이상. 회차에는 조정값만 둔다", example = "30000", requiredMode = REQUIRED)
        @NotNull(message = "기본가를 입력해 주세요.")
        @ZoneBasePriceRule
        Long basePrice,

        @Schema(description = "정원 — 수량형만 필수로 적는다(1 이상). 지정형은 비우거나 생략한다 — 배정한 좌석이 자리다. 수량형인데 비우면 409 "
                + "ZONE_CAPACITY_REQUIRED, 지정형인데 적으면 409 ZONE_CAPACITY_ONLY_FOR_QUANTITY", example = "300",
                requiredMode = NOT_REQUIRED, nullable = true)
        @ZoneCapacityRule
        Integer capacity) {

    AddZoneCommand toCommand(ActingStaff actingStaff, long eventId) {
        return new AddZoneCommand(actingStaff, eventId, kind.toZoneKind(), ZoneRequests.settings(name, color, basePrice, capacity));
    }
}
