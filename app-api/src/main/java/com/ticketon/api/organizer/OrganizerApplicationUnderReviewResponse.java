package com.ticketon.api.organizer;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.organizer.port.in.OrganizerHomeResult;
import com.ticketon.application.organizer.port.in.SubmitOrganizerApplicationResult;
import com.ticketon.common.KoreanTime;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * 심사 중인 주최자 신청 — 신청서를 낸 응답이자 주최 홈의 "심사 중" 칸이다. 화면은 이것으로 "심사 중" 화면을 보인다
 * (docs/01 "승인 전까지 '심사 중' 화면만"). 담당자 연락처 · 사업자 정보 · 정산 계좌는 돌려주지 않는다.
 *
 * @param applicationId 신청서 id
 * @param organizerName 신청한 이름 또는 단체명
 * @param submittedAt   제출 일시 — 한국 시각
 */
@Schema(description = "심사 중인 주최자 신청")
public record OrganizerApplicationUnderReviewResponse(
        @Schema(description = "신청서 id", example = "3", requiredMode = REQUIRED)
        long applicationId,

        @Schema(description = "신청한 이름 또는 단체명", example = "극단 열두달", requiredMode = REQUIRED)
        String organizerName,

        @Schema(description = "제출 일시 — 한국 시각(+09:00)", example = "2026-10-15T10:00:00+09:00", requiredMode = REQUIRED)
        OffsetDateTime submittedAt) {

    static OrganizerApplicationUnderReviewResponse from(SubmitOrganizerApplicationResult result) {
        return new OrganizerApplicationUnderReviewResponse(result.applicationId(), result.organizerName(),
                koreanTime(result.submittedAt()));
    }

    static OrganizerApplicationUnderReviewResponse from(OrganizerHomeResult.ApplicationUnderReview application) {
        return new OrganizerApplicationUnderReviewResponse(application.applicationId(), application.organizerName(),
                koreanTime(application.submittedAt()));
    }

    static OffsetDateTime koreanTime(Instant instant) {
        return instant.atZone(KoreanTime.ZONE).toOffsetDateTime();
    }
}
