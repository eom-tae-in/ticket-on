package com.ticketon.api.account;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.api.account.AudienceOAuthSession.PendingSignUp;
import com.ticketon.api.account.AudienceOAuthSession.Result;
import com.ticketon.api.error.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Optional;

/**
 * 카카오·구글 로그인 결과 — 결과 페이지가 이것을 보고 다음 화면을 정한다.
 *
 * @param outcome       결과
 * @param provider      제공자. 시작 정보 없이 돌아온 실패면 null.
 * @param returnPath    화면이 이어서 갈 경로 — 로그인을 시작할 때 보낸 경로(같은 사이트 안의 경로만)이거나 관객 첫 화면
 * @param pendingSignUp 추가 정보 입력 화면을 미리 채울 값 — SIGNUP_REQUIRED일 때만
 * @param error         실패 까닭 — FAILED일 때만
 */
@Schema(description = "카카오·구글 로그인 결과")
public record OAuthLoginResultResponse(
        @Schema(description = "결과 — LOGGED_IN: 로그인됨(returnPath로 이동), SIGNUP_REQUIRED: 추가 정보 입력 후 가입, "
                + "LINKED: 마이페이지 연결 완료, FAILED: 실패(error.message를 띄움)", requiredMode = REQUIRED)
        OAuthLoginOutcome outcome,

        @Schema(description = "제공자. 시작 정보 없이 돌아온 실패면 null", requiredMode = NOT_REQUIRED, nullable = true)
        OAuthProviderCode provider,

        @Schema(description = "화면이 이어서 갈 경로 — 로그인·연결을 시작할 때 보낸 returnPath(같은 사이트 안의 경로만)이거나 관객 첫 화면(/)",
                example = "/events/12/booking", requiredMode = REQUIRED)
        String returnPath,

        @Schema(description = "추가 정보 입력 화면을 미리 채울 값 — SIGNUP_REQUIRED일 때만", requiredMode = NOT_REQUIRED, nullable = true)
        OAuthPendingSignUpResponse pendingSignUp,

        @Schema(description = "실패 까닭 — FAILED일 때만. 화면은 code로 분기하고 message를 그대로 띄운다", requiredMode = NOT_REQUIRED,
                nullable = true)
        ErrorResponse error) {

    static OAuthLoginResultResponse of(Result result, Optional<PendingSignUp> pendingSignUp) {
        ErrorResponse error = result.outcome() == OAuthLoginOutcome.FAILED
                ? new ErrorResponse(result.errorCode(), result.errorMessage(), List.of()) : null;
        OAuthPendingSignUpResponse pendingSignUpResponse = result.outcome() == OAuthLoginOutcome.SIGNUP_REQUIRED
                ? pendingSignUp.map(OAuthPendingSignUpResponse::from).orElse(null) : null;
        return new OAuthLoginResultResponse(result.outcome(), result.provider(), result.returnPath(), pendingSignUpResponse,
                error);
    }
}
