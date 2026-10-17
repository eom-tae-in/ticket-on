package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.application.event.port.in.ChangeZoneCommand;
import com.ticketon.application.organizer.port.in.ActingStaff;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 구역 수정 요청 — 위저드 3단계에서 구역 한 줄을 저장할 때 칸을 모두 보낸다. 칸 이름은 구역 응답과 같다 — 받은 값을 고쳐 그대로 보내면 된다(종류 ·
 * id는 빼고). 종류는 바꾸지 않는다.
 * <p>
 * 칸 검증(400)은 구역 추가와 같다. 정원이 저장된 구역의 종류와 맞는지, 이름이 겹치는지, 바뀐 칸을 지금 고칠 수 있는지는 유스케이스가 본다(409).
 *
 * @param name      이름
 * @param color     색
 * @param basePrice 기본가(원)
 * @param capacity  정원 — 수량형만
 */
@Schema(description = "구역 수정 (위저드 3단계)")
public record ChangeZoneRequest(
        @Schema(description = "이름 — 앞뒤 공백은 걷는다. 20자까지(확인 필요)", example = "스탠딩A", requiredMode = REQUIRED)
        @FilledText(message = "구역 이름을 입력해 주세요.")
        @ZoneNameRule
        String name,

        @Schema(description = "색 — #RRGGBB", example = "#30A46C", requiredMode = REQUIRED)
        @FilledText(message = "구역 색을 골라 주세요.")
        @ZoneColorRule
        String color,

        @Schema(description = "기본가(원) — 1 이상. 판매 중에 바꾸면 신규 예매부터 적용된다", example = "30000", requiredMode = REQUIRED)
        @NotNull(message = "기본가를 입력해 주세요.")
        @ZoneBasePriceRule
        Long basePrice,

        @Schema(description = "정원 — 수량형 구역만 필수로 적는다(1 이상). 지정형 구역은 비우거나 생략한다. 수량형인데 비우면 409 "
                + "ZONE_CAPACITY_REQUIRED, 지정형인데 적으면 409 ZONE_CAPACITY_ONLY_FOR_QUANTITY", example = "500",
                requiredMode = NOT_REQUIRED, nullable = true)
        @ZoneCapacityRule
        Integer capacity) {

    ChangeZoneCommand toCommand(ActingStaff actingStaff, long eventId, long zoneId) {
        return new ChangeZoneCommand(actingStaff, eventId, zoneId, ZoneRequests.settings(name, color, basePrice, capacity));
    }
}
