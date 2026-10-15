package com.ticketon.api.admin;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.admin.port.in.RejectOrganizerApplicationResult;
import com.ticketon.common.KoreanTime;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 주최자 신청 반려 응답.
 *
 * @param applicationId   반려한 신청서 id
 * @param organizerName   신청한 이름 또는 단체명
 * @param rejectionReason 전달한 반려 사유 — 앞뒤 공백을 걷은 값
 * @param rejectedAt      반려 일시 — 한국 시각
 */
@Schema(description = "주최자 신청 반려 응답")
public record RejectOrganizerApplicationResponse(
        @Schema(description = "반려한 신청서 id", example = "3", requiredMode = REQUIRED)
        long applicationId,

        @Schema(description = "신청한 이름 또는 단체명", example = "극단 열두달", requiredMode = REQUIRED)
        String organizerName,

        @Schema(description = "신청한 사람에게 전달한 반려 사유 — 앞뒤 공백을 걷은 값", example = "연락이 닿지 않음",
                requiredMode = REQUIRED)
        String rejectionReason,

        @Schema(description = "반려 일시 — 한국 시각(+09:00)", example = "2026-10-17T15:00:00+09:00", requiredMode = REQUIRED)
        OffsetDateTime rejectedAt) {

    static RejectOrganizerApplicationResponse from(RejectOrganizerApplicationResult result) {
        return new RejectOrganizerApplicationResponse(result.applicationId(), result.organizerName(),
                result.rejectionReason(), result.rejectedAt().atZone(KoreanTime.ZONE).toOffsetDateTime());
    }
}
