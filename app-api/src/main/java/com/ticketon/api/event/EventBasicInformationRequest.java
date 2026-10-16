package com.ticketon.api.event;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.validation.FilledText;
import com.ticketon.application.event.port.in.EventBasicInformationItems;
import com.ticketon.domain.event.EventPublicContact;
import com.ticketon.domain.event.EventTitle;
import com.ticketon.domain.event.Venue;
import com.ticketon.domain.organizer.ExternalLink;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 위저드 1단계(기본 정보) 요청 — 행사 만들기와 1단계 저장이 같은 모양이다(docs/03 "기본 정보"). 단계의 칸을 모두 보낸다. 포스터는 따로 올린다.
 * <p>
 * 칸마다 비었는지 · 길이 · 형식은 여기서 도메인 값 객체와 같은 규칙으로 본다(400 — 전각 공백만 넣은 칸도 빈 칸이다, {@link FilledText},
 * {@link EventTitleRule}, {@link EventCategoryNameRule}, {@link OptionalExternalLinkFormat}). 고른 카테고리가 그 유형의 목록에 있는지, '기타'에
 * 장르명이 있는지는 저장된 카테고리 목록으로 유스케이스가 본다(409). 선택 칸(상세 안내 · 지도 링크 · 공개용 연락처)은 비우거나 공백만 보내면 적지
 * 않은 것이다. 소개 · 장소 · 상세 안내의 길이는 문서에 상한이 없어 두지 않는다.
 * <p>
 * 공개용 연락처는 연락처라 {@link #toString}에 싣지 않는다.
 *
 * @param title         제목
 * @param type          유형
 * @param categoryName  고른 카테고리 이름 — 기타면 "기타"
 * @param genreName     기타를 고르고 적은 장르명
 * @param introduction  소개
 * @param venueName     공연장 이름
 * @param venueAddress  주소
 * @param venueDetails  상세 안내
 * @param mapLink       지도 링크
 * @param publicContact 공개용 연락처
 */
@Schema(description = "위저드 1단계(기본 정보) — 행사 만들기와 1단계 저장")
public record EventBasicInformationRequest(
        @Schema(description = "제목 — 앞뒤 공백은 걷는다. 100자까지(확인 필요)", example = "2026 봄 정기공연 〈열두 번째 달〉",
                requiredMode = REQUIRED)
        @FilledText(message = "제목을 입력해 주세요.")
        @EventTitleRule
        String title,

        @Schema(description = "유형 — 고른 유형의 카테고리 목록에서 카테고리를 고른다", requiredMode = REQUIRED)
        @NotNull(message = "유형을 골라 주세요.")
        EventTypeCode type,

        @Schema(description = "고른 카테고리 이름 — 그 유형의 정식 카테고리(GET /api/organizer/event-categories) 또는 \"기타\"",
                example = "연극", requiredMode = REQUIRED)
        @FilledText(message = "카테고리를 골라 주세요.")
        @EventCategoryNameRule(message = "카테고리는 목록에서 골라 주세요.")
        String categoryName,

        @Schema(description = "장르명 — 카테고리 \"기타\"를 고를 때만 필수로 적는다. 정식 카테고리면 비우거나 생략. 20자까지(확인 필요)",
                example = "낭독극", requiredMode = NOT_REQUIRED, nullable = true)
        @EventCategoryNameRule(message = "장르명은 20자 이하로 입력해 주세요.")
        String genreName,

        @Schema(description = "소개 — 앞뒤 공백은 걷는다", example = "극단 열두달이 2019년부터 해마다 올리는 봄 정기공연입니다.",
                requiredMode = REQUIRED)
        @FilledText(message = "소개를 입력해 주세요.")
        String introduction,

        @Schema(description = "공연장 이름", example = "○○아트홀", requiredMode = REQUIRED)
        @FilledText(message = "공연장 이름을 입력해 주세요.")
        String venueName,

        @Schema(description = "주소 — 누르면 외부 지도 앱으로 넘어간다", example = "서울특별시 종로구 대학로 12", requiredMode = REQUIRED)
        @FilledText(message = "주소를 입력해 주세요.")
        String venueAddress,

        @Schema(description = "상세 안내 (선택) — \"지하 1층\", \"주차 불가\" 등. 비우면 없음", example = "지하 1층, 주차 불가",
                requiredMode = NOT_REQUIRED, nullable = true)
        String venueDetails,

        @Schema(description = "지도 링크 (선택) — http:// 또는 https://로 시작하는 외부 지도 주소. 비우면 없음",
                example = "https://map.example/place/12", requiredMode = NOT_REQUIRED, nullable = true)
        @OptionalExternalLinkFormat
        String mapLink,

        @Schema(description = "공개용 연락처 (선택) — 적으면 행사 페이지에 보인다. 형식은 보지 않는다. 비우면 없음",
                example = "twelve-months@example.com", requiredMode = NOT_REQUIRED, nullable = true)
        String publicContact) {

    EventBasicInformationItems toItems() {
        return new EventBasicInformationItems(EventTitle.of(title), type.toEventType(), categoryName, genreName, introduction,
                new Venue(venueName, venueAddress), venueDetails, isBlank(mapLink) ? null : ExternalLink.of(mapLink),
                isBlank(publicContact) ? null : EventPublicContact.of(publicContact));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Override
    public String toString() {
        return "EventBasicInformationRequest[title=" + title + ", type=" + type + ", categoryName=" + categoryName
                + ", genreName=" + genreName + ", venueName=" + venueName + ", publicContact="
                + (publicContact == null ? null : "****") + "]";
    }
}
