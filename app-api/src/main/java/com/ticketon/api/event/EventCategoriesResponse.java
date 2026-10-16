package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.event.port.in.EventCategoriesResult;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 정식 카테고리 목록 — 위저드 1단계에서 유형을 고르면 그에 맞는 카테고리가 나온다(docs/03 "카테고리"). 화면은 유형마다 목록 끝에
 * {@code otherCategoryName}("기타")을 붙이고, 그것을 고르면 장르명 칸을 연다.
 *
 * @param types             유형마다의 정식 카테고리
 * @param otherCategoryName 모든 유형의 목록 끝에 붙는 선택의 이름
 */
@Schema(description = "정식 카테고리 목록")
public record EventCategoriesResponse(
        @ArraySchema(arraySchema = @Schema(description = "유형마다의 정식 카테고리 — 유형 차례(공연 · 전시 · 페스티벌)", requiredMode = REQUIRED))
        List<EventTypeCategoriesResponse> types,

        @Schema(description = "모든 유형의 목록 끝에 붙는 선택의 이름 — 고르면 장르명을 자유 입력받는다", example = "기타", requiredMode = REQUIRED)
        String otherCategoryName) {

    static EventCategoriesResponse from(EventCategoriesResult result) {
        return new EventCategoriesResponse(result.types().stream()
                .map(type -> new EventTypeCategoriesResponse(EventTypeCode.from(type.type()), type.categoryNames()))
                .toList(), result.otherCategoryName());
    }

    /**
     * 한 유형의 정식 카테고리.
     *
     * @param type          유형
     * @param categoryNames 정식 카테고리 이름 — 보여 줄 순서대로. "기타"는 담지 않는다
     */
    @Schema(description = "한 유형의 정식 카테고리")
    public record EventTypeCategoriesResponse(
            @Schema(description = "유형", requiredMode = REQUIRED)
            EventTypeCode type,

            @ArraySchema(arraySchema = @Schema(description = "정식 카테고리 이름 — 보여 줄 순서대로. \"기타\"는 담지 않는다",
                    requiredMode = REQUIRED), schema = @Schema(example = "연극"))
            List<String> categoryNames) {
    }
}
