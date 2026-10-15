package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.account.port.in.GetMyAudienceAccountResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 내 관객 계정 응답 — 마이페이지 "계정 정보"(docs/13)와, 화면이 지금 누가 로그인했는지 알 때 쓴다. 계정 id는 내보내지 않는다.
 *
 * @param name                 이름
 * @param email                이메일(가입할 때 적은 그대로)
 * @param phoneNumber          전화번호 — 숫자만 남긴 11자리
 * @param suspended            이용 정지 중인지
 * @param linkedOAuthProviders 연결된 카카오·구글 — 카카오, 구글 순서
 */
@Schema(description = "내 관객 계정")
public record MyAudienceAccountResponse(
        @Schema(description = "이름", example = "한서윤", requiredMode = REQUIRED)
        String name,

        @Schema(description = "이메일 — 가입할 때 적은 그대로", example = "seoyun@example.com", requiredMode = REQUIRED)
        String email,

        @Schema(description = "전화번호 — 숫자만 남긴 11자리", example = "01012345678", requiredMode = REQUIRED)
        String phoneNumber,

        @Schema(description = "이용 정지 중인지. 정지 중에는 새 예매·변경·신고가 막히고, 로그인·기존 티켓 보기·QR·취소는 된다",
                requiredMode = REQUIRED)
        boolean suspended,

        @Schema(description = "연결된 카카오·구글 — 카카오, 구글 순서. 연결이 없으면 빈 목록", requiredMode = REQUIRED)
        List<OAuthProviderCode> linkedOAuthProviders) {

    static MyAudienceAccountResponse from(GetMyAudienceAccountResult result) {
        return new MyAudienceAccountResponse(result.name(), result.email(), result.phoneNumber(), result.suspended(),
                result.linkedOAuthProviders().stream().map(OAuthProviderCode::from).toList());
    }
}
