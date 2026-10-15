package com.ticketon.api.organizer;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.account.InitialAvatarResponse;
import com.ticketon.application.organizer.port.in.OrganizerPublicProfileResult;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 주최자 공개 정보 — 소개 · SNS 링크 · 로고와, 로고가 없을 때 로고 자리에 보이는 첫 글자 표시(docs/01 "공개 페이지 — 주최자 페이지"). 주최 관리의
 * "주최자 공개 정보" 화면이 이 값으로 편집 칸을 채운다.
 *
 * @param organizerId  주최자 id
 * @param introduction 소개. 적지 않았으면 null.
 * @param snsLinks     SNS 링크 — 적은 순서대로
 * @param logoUrl      로고의 공개 주소(절대 주소). 로고가 없으면 null.
 * @param avatar       로고가 없을 때 로고 자리에 보이는 첫 글자 표시
 */
@Schema(description = "주최자 공개 정보")
public record OrganizerPublicProfileResponse(
        @Schema(description = "주최자 id", example = "12", requiredMode = REQUIRED)
        long organizerId,

        @Schema(description = "소개 — 앞뒤 공백을 걷어 저장된 그대로. 적지 않았으면 null", example = "2019년부터 소극장 정기공연을 해마다 두 번 올립니다.",
                requiredMode = NOT_REQUIRED, nullable = true)
        String introduction,

        @ArraySchema(arraySchema = @Schema(description = "SNS 링크 — 적은 순서대로. 없으면 빈 배열. 심사용으로 낸 링크와 별개다",
                requiredMode = REQUIRED),
                schema = @Schema(description = "http:// 또는 https://로 시작하는 주소",
                        example = "https://www.instagram.com/twelve_months_theater"))
        List<String> snsLinks,

        @Schema(description = "로고의 공개 주소 — 로그인 없이 누구나 여는 절대 주소. 화면은 그림 주소로 그대로 쓴다. 로고가 없으면 null이고, "
                + "그때는 avatar(이름 첫 글자 + 정해진 색)를 로고 자리에 보인다",
                example = "http://localhost:8080/api/public/files/organizer-logos/Xb3kQ9vL2mN8pR4tW7yZ1aC5dF6gH0jK.png",
                requiredMode = NOT_REQUIRED, nullable = true)
        String logoUrl,

        @Schema(description = "로고가 없을 때 로고 자리에 보이는 첫 글자 표시(주최자명 첫 글자 + 주최자마다 정해진 색). 로고가 있어도 담는다",
                requiredMode = REQUIRED)
        InitialAvatarResponse avatar) {

    static OrganizerPublicProfileResponse from(OrganizerPublicProfileResult result) {
        return new OrganizerPublicProfileResponse(result.organizerId(), result.introduction(), result.snsLinks(),
                result.logoUrl(), new InitialAvatarResponse(result.initial(), result.paletteIndex()));
    }
}
