package com.ticketon.api.organizer;

import static com.ticketon.api.organizer.OrganizerApplicationUnderReviewResponse.koreanTime;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.organizer.port.in.InviteOperatorResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * 보낸 운영자 초대.
 *
 * @param invitationId 초대 id
 * @param inviteeEmail 초대받은 이메일
 * @param invitedAt    보낸 일시 — 한국 시각
 * @param expiresAt    유효 기간이 끝나는 일시 — 한국 시각
 */
@Schema(description = "보낸 운영자 초대")
public record StaffInvitationResponse(
        @Schema(description = "초대 id", example = "51", requiredMode = REQUIRED)
        long invitationId,

        @Schema(description = "초대받은 이메일 — 적은 그대로", example = "dohyun@example.com", requiredMode = REQUIRED)
        String inviteeEmail,

        @Schema(description = "보낸 일시 — 한국 시각(+09:00). 지난 초대를 다시 보냈으면 다시 보낸 일시", example = "2026-10-15T10:00:00+09:00",
                requiredMode = REQUIRED)
        OffsetDateTime invitedAt,

        @Schema(description = "유효 기간이 끝나는 일시 — 한국 시각(+09:00). 이 순간부터 받은 사람이 답할 수 없다",
                example = "2026-10-22T10:00:00+09:00", requiredMode = REQUIRED)
        OffsetDateTime expiresAt) {

    static StaffInvitationResponse from(InviteOperatorResult result) {
        return new StaffInvitationResponse(result.invitationId(), result.inviteeEmail(), koreanTime(result.invitedAt()),
                koreanTime(result.expiresAt()));
    }
}
