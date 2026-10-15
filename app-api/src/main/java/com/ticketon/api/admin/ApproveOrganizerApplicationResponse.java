package com.ticketon.api.admin;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.admin.port.in.ApproveOrganizerApplicationResult;
import com.ticketon.common.KoreanTime;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 주최자 신청 승인 응답.
 *
 * @param applicationId       승인한 신청서 id
 * @param organizerId         활동이 된 주최자 id
 * @param organizerName       주최자명
 * @param ownerStaffAccountId 소유자가 된 사람의 운영진 계정 id
 * @param approvedAt          승인 일시 — 한국 시각
 */
@Schema(description = "주최자 신청 승인 응답")
public record ApproveOrganizerApplicationResponse(
        @Schema(description = "승인한 신청서 id", example = "3", requiredMode = REQUIRED)
        long applicationId,

        @Schema(description = "활동이 된 주최자 id", example = "12", requiredMode = REQUIRED)
        long organizerId,

        @Schema(description = "주최자명 — 신청한 이름 또는 단체명", example = "극단 열두달", requiredMode = REQUIRED)
        String organizerName,

        @Schema(description = "소유자가 된 사람(신청한 사람)의 운영진 계정 id", example = "5", requiredMode = REQUIRED)
        long ownerStaffAccountId,

        @Schema(description = "승인 일시 — 한국 시각(+09:00)", example = "2026-10-17T15:00:00+09:00", requiredMode = REQUIRED)
        OffsetDateTime approvedAt) {

    static ApproveOrganizerApplicationResponse from(ApproveOrganizerApplicationResult result) {
        return new ApproveOrganizerApplicationResponse(result.applicationId(), result.organizerId(),
                result.organizerName(), result.ownerStaffAccountId(),
                result.approvedAt().atZone(KoreanTime.ZONE).toOffsetDateTime());
    }
}
