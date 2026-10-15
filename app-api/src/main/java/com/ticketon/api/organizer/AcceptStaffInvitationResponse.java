package com.ticketon.api.organizer;

import static com.ticketon.api.organizer.OrganizerApplicationUnderReviewResponse.koreanTime;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.organizer.port.in.AcceptStaffInvitationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 운영자 초대를 수락해 들어간 주최자 — 화면은 주최자 전환으로 바로 이 주최자를 고른다. 역할은 운영자다.
 *
 * @param organizerId   들어간 주최자 id
 * @param organizerName 들어간 주최자명
 * @param joinedAt      운영자 소속을 시작한 일시 — 한국 시각
 */
@Schema(description = "운영자 초대를 수락해 들어간 주최자")
public record AcceptStaffInvitationResponse(
        @Schema(description = "들어간 주최자 id", example = "12", requiredMode = REQUIRED)
        long organizerId,

        @Schema(description = "들어간 주최자명", example = "극단 열두달", requiredMode = REQUIRED)
        String organizerName,

        @Schema(description = "운영자 소속을 시작한 일시 — 한국 시각(+09:00)", example = "2026-10-16T21:00:00+09:00",
                requiredMode = REQUIRED)
        OffsetDateTime joinedAt) {

    static AcceptStaffInvitationResponse from(AcceptStaffInvitationResult result) {
        return new AcceptStaffInvitationResponse(result.organizerId(), result.organizerName(),
                koreanTime(result.joinedAt()));
    }
}
