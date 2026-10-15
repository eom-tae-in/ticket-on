package com.ticketon.api.organizer;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;

import com.ticketon.application.organizer.port.in.ActingStaff;
import com.ticketon.application.organizer.port.in.ChangeOrganizerPublicProfileCommand;
import com.ticketon.domain.organizer.ExternalLink;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 주최자 공개 정보(소개 · SNS 링크) 편집 요청 — 둘 다 선택이고, 보낸 값으로 둘 다 바꾼다(docs/01 "공개 페이지 — 편집"). 소개를 비우거나 보내지
 * 않으면 소개를 지우고, 링크를 빈 배열로 보내거나 보내지 않으면 링크를 모두 지운다. 로고는 따로 올리고 지운다.
 * <p>
 * 링크 형식은 도메인 {@link ExternalLink}와 같은 규칙(http(s) 절대 주소)으로 본다(400). 소개 길이와 링크 개수는 문서에 상한이 없어 두지 않는다.
 */
@Schema(description = "주최자 공개 정보(소개 · SNS 링크) 편집 요청")
public record ChangeOrganizerPublicProfileRequest(
        @Schema(description = "소개. 선택 — 비우거나 보내지 않으면 소개를 지운다. 앞뒤 공백은 걷는다",
                example = "2019년부터 소극장 정기공연을 해마다 두 번 올립니다.", requiredMode = NOT_REQUIRED, nullable = true)
        String introduction,

        @ArraySchema(arraySchema = @Schema(description = "SNS 링크 (선택) — 적은 순서대로 주최자 페이지에 보인다. 심사용으로 낸 링크와 별개다. "
                + "없으면 빈 배열이나 생략", requiredMode = NOT_REQUIRED),
                schema = @Schema(description = "http:// 또는 https://로 시작하는 주소",
                        example = "https://www.instagram.com/twelve_months_theater"))
        List<@ExternalLinkFormat String> snsLinks) {

    ChangeOrganizerPublicProfileCommand toCommand(ActingStaff actingStaff) {
        List<ExternalLink> links = snsLinks == null ? List.of() : snsLinks.stream().map(ExternalLink::of).toList();
        return new ChangeOrganizerPublicProfileCommand(actingStaff, introduction, links);
    }
}
