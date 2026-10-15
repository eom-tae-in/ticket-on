package com.ticketon.api.organizer;

import static com.ticketon.api.organizer.OrganizerApplicationUnderReviewResponse.koreanTime;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.organizer.port.in.RequestOrganizerChangeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 낸 변경 요청 — 관리자 확인 중이다. 확인 중에는 기존 값이 그대로 쓰이고, 결과(승인 · 반려)는 소유자에게 메일로 간다.
 *
 * @param changeRequestId 변경 요청 id
 * @param kind            바꾸려는 것의 종류
 * @param requestedAt     요청 일시 — 한국 시각
 */
@Schema(description = "낸 정산 계좌 · 사업자 정보 변경 요청")
public record OrganizerChangeRequestResponse(
        @Schema(description = "변경 요청 id", example = "8", requiredMode = REQUIRED)
        long changeRequestId,

        @Schema(description = "바꾸려는 것의 종류", requiredMode = REQUIRED)
        OrganizerChangeKindCode kind,

        @Schema(description = "요청 일시 — 한국 시각(+09:00)", example = "2026-10-15T10:00:00+09:00", requiredMode = REQUIRED)
        OffsetDateTime requestedAt) {

    static OrganizerChangeRequestResponse from(RequestOrganizerChangeResult result) {
        return new OrganizerChangeRequestResponse(result.changeRequestId(), OrganizerChangeKindCode.from(result.kind()),
                koreanTime(result.requestedAt()));
    }
}
