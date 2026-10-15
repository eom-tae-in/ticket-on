package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 첫 글자 표시 — 운영진 프로필에 사진 대신 보이는 닉네임 첫 글자와 사람마다 정해진 색(docs/01 "운영진 프로필 — 표시"), 로고가 없는 주최자의
 * 로고 자리에 보이는 주최자명 첫 글자와 주최자마다 정해진 색(docs/01 "공개 페이지 — 로고"). 둘이 같은 8가지 팔레트를 쓴다. 팔레트의 실제 색
 * 값은 아직 정하지 않아(확인 과제 #135) 번호만 내보내고, 번호를 색으로 바꾸는 일은 화면이 한다.
 *
 * @param initial      첫 글자 — 한 글자(한글 한 자, 이모지 하나)
 * @param paletteIndex 팔레트 번호 — 0부터 7까지
 */
@Schema(description = "첫 글자 표시 — 사진이나 로고 대신 보이는 첫 글자와 정해진 색의 팔레트 번호")
public record InitialAvatarResponse(
        @Schema(description = "첫 글자 — 한 글자(한글 한 자, 이모지 하나)", example = "조", requiredMode = REQUIRED)
        String initial,

        @Schema(description = "정해진 색의 팔레트 번호 — 0부터 7까지(운영진 프로필과 주최자 로고가 같은 8가지). 같은 사람 · 같은 주최자는 "
                + "닉네임 · 주최자명을 바꿔도 번호가 그대로다. 번호를 색으로 바꾸는 일은 화면이 한다", example = "1", minimum = "0",
                maximum = "7", requiredMode = REQUIRED)
        int paletteIndex) {
}
