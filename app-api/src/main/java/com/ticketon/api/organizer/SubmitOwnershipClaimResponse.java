package com.ticketon.api.organizer;

import static com.ticketon.api.organizer.OrganizerApplicationUnderReviewResponse.koreanTime;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.organizer.port.in.SubmitOwnershipClaimResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 낸 소유자 이전 신청 — 심사 중이다. 결과(승인 · 반려)는 운영진 전원에게 메일로 간다.
 *
 * @param ownershipClaimId 신청 id
 * @param submittedAt      신청 일시 — 한국 시각
 */
@Schema(description = "낸 소유자 이전 신청")
public record SubmitOwnershipClaimResponse(
        @Schema(description = "신청 id", example = "4", requiredMode = REQUIRED)
        long ownershipClaimId,

        @Schema(description = "신청 일시 — 한국 시각(+09:00)", example = "2026-10-15T10:00:00+09:00", requiredMode = REQUIRED)
        OffsetDateTime submittedAt) {

    static SubmitOwnershipClaimResponse from(SubmitOwnershipClaimResult result) {
        return new SubmitOwnershipClaimResponse(result.ownershipClaimId(), koreanTime(result.submittedAt()));
    }
}
