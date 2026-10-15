package com.ticketon.api.organizer;

import static com.ticketon.api.organizer.OrganizerApplicationUnderReviewResponse.koreanTime;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.organizer.port.in.OrganizerHomeResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 내 주최 홈 상태 — 화면이 어느 갈래를 보일지 정하는 재료(docs/13 "3. 주최 관리 — 로그인").
 * <pre>
 * 로그인
 *  ├─ 소속된 주최자가 없으면 → 새 주최자 신청 / 받은 초대 / 닫힌 주최자 다시 열기 신청 (소유자였던 경우)
 *  ├─ 신청이 심사 중이면 → "심사 중" 화면만
 *  └─ 여러 곳에 소속돼 있으면 → 주최자 전환
 * </pre>
 * 신청서의 담당자 연락처 · 사업자 정보 · 정산 계좌는 담지 않는다.
 *
 * @param affiliations           지금 소속된 주최자
 * @param applicationUnderReview 심사 중인 신청. 없으면 null.
 * @param rejectedApplication    가장 최근 신청이 반려됐으면 그 신청. 아니면 null.
 * @param receivedInvitations    받은 운영자 초대
 * @param reopenableOrganizers   다시 열기를 신청할 수 있는 닫힌 주최자
 */
@Schema(description = "내 주최 홈 상태")
public record OrganizerHomeResponse(
        @Schema(description = "지금 소속된 주최자 — 소속을 시작한 순서. 비어 있으면 소속 없음이고, 둘 이상이면 주최자 전환을 보인다",
                requiredMode = REQUIRED)
        List<OrganizerAffiliation> affiliations,

        @Schema(description = "심사 중인 신청 — 있으면 \"심사 중\" 화면을 보인다. 없으면 null", requiredMode = NOT_REQUIRED,
                nullable = true)
        OrganizerApplicationUnderReviewResponse applicationUnderReview,

        @Schema(description = "가장 최근 신청이 반려됐으면 그 신청과 사유 — 다시 신청할 수 있다. 다시 신청하면 사라진다. 아니면 null",
                requiredMode = NOT_REQUIRED, nullable = true)
        RejectedOrganizerApplication rejectedApplication,

        @Schema(description = "받은 운영자 초대 — 운영진 로그인 이메일로 와서 답을 기다리고 유효 기간이 남은 것, 보낸 순서. "
                + "소속이 없으면 \"새 주최자 신청\"과 함께 보인다. 없으면 빈 목록", requiredMode = REQUIRED)
        List<ReceivedOrganizerInvitation> receivedInvitations,

        @Schema(description = "다시 열기를 신청할 수 있는 닫힌 주최자 — 닫힌 주최자의 소유자였던 경우. 없으면 빈 목록 "
                + "(주최자 닫기 기능이 생기기 전까지 늘 빈 목록)", requiredMode = REQUIRED)
        List<ReopenableOrganizer> reopenableOrganizers) {

    static OrganizerHomeResponse from(OrganizerHomeResult result) {
        return new OrganizerHomeResponse(
                result.affiliations().stream()
                        .map(affiliation -> new OrganizerAffiliation(affiliation.organizerId(), affiliation.organizerName(),
                                StaffRoleCode.from(affiliation.role()), koreanTime(affiliation.since())))
                        .toList(),
                result.applicationUnderReview() == null ? null
                        : OrganizerApplicationUnderReviewResponse.from(result.applicationUnderReview()),
                result.rejectedApplication() == null ? null : RejectedOrganizerApplication.from(result.rejectedApplication()),
                result.receivedInvitations().stream()
                        .map(ReceivedOrganizerInvitation::from)
                        .toList(),
                result.reopenableOrganizers().stream()
                        .map(organizer -> new ReopenableOrganizer(organizer.organizerId(), organizer.organizerName()))
                        .toList());
    }

    /**
     * @param organizerId   주최자 id
     * @param organizerName 주최자명
     * @param role          그 주최자에서의 역할
     * @param since         지금 역할로 소속된 일시 — 한국 시각
     */
    @Schema(description = "지금 소속된 주최자 하나")
    public record OrganizerAffiliation(
            @Schema(description = "주최자 id", example = "12", requiredMode = REQUIRED)
            long organizerId,

            @Schema(description = "주최자명", example = "극단 열두달", requiredMode = REQUIRED)
            String organizerName,

            @Schema(description = "그 주최자에서의 역할 — 운영자에게는 소유자 전용 메뉴를 감춘다", requiredMode = REQUIRED)
            StaffRoleCode role,

            @Schema(description = "지금 역할로 소속된 일시 — 한국 시각(+09:00)", example = "2026-10-17T15:00:00+09:00",
                    requiredMode = REQUIRED)
            OffsetDateTime since) {
    }

    /**
     * @param applicationId   신청서 id
     * @param organizerName   신청한 이름 또는 단체명
     * @param submittedAt     제출 일시 — 한국 시각
     * @param rejectedAt      반려 일시 — 한국 시각
     * @param rejectionReason 반려 사유
     */
    @Schema(description = "반려된 주최자 신청")
    public record RejectedOrganizerApplication(
            @Schema(description = "신청서 id", example = "3", requiredMode = REQUIRED)
            long applicationId,

            @Schema(description = "신청한 이름 또는 단체명", example = "극단 열두달", requiredMode = REQUIRED)
            String organizerName,

            @Schema(description = "제출 일시 — 한국 시각(+09:00)", example = "2026-10-15T10:00:00+09:00", requiredMode = REQUIRED)
            OffsetDateTime submittedAt,

            @Schema(description = "반려 일시 — 한국 시각(+09:00)", example = "2026-10-17T15:00:00+09:00", requiredMode = REQUIRED)
            OffsetDateTime rejectedAt,

            @Schema(description = "반려 사유 — 관리자가 남긴 그대로", example = "연락이 닿지 않음", requiredMode = REQUIRED)
            String rejectionReason) {

        static RejectedOrganizerApplication from(OrganizerHomeResult.RejectedApplication application) {
            return new RejectedOrganizerApplication(application.applicationId(), application.organizerName(),
                    koreanTime(application.submittedAt()), koreanTime(application.rejectedAt()),
                    application.rejectionReason());
        }
    }

    /**
     * @param invitationId  초대 id
     * @param organizerId   초대한 주최자 id
     * @param organizerName 초대한 주최자명
     * @param invitedAt     보낸 일시 — 한국 시각
     * @param expiresAt     유효 기간이 끝나는 일시 — 한국 시각
     */
    @Schema(description = "받은 운영자 초대 하나 — 수락하면 그 주최자의 운영자가 된다")
    public record ReceivedOrganizerInvitation(
            @Schema(description = "초대 id — 수락 · 거절할 때 쓴다", example = "51", requiredMode = REQUIRED)
            long invitationId,

            @Schema(description = "초대한 주최자 id", example = "30", requiredMode = REQUIRED)
            long organizerId,

            @Schema(description = "초대한 주최자명", example = "늦여름", requiredMode = REQUIRED)
            String organizerName,

            @Schema(description = "보낸 일시 — 한국 시각(+09:00)", example = "2026-10-15T10:00:00+09:00", requiredMode = REQUIRED)
            OffsetDateTime invitedAt,

            @Schema(description = "유효 기간이 끝나는 일시 — 한국 시각(+09:00). 이 순간부터 수락 · 거절할 수 없다",
                    example = "2026-10-22T10:00:00+09:00", requiredMode = REQUIRED)
            OffsetDateTime expiresAt) {

        static ReceivedOrganizerInvitation from(OrganizerHomeResult.ReceivedInvitation invitation) {
            return new ReceivedOrganizerInvitation(invitation.invitationId(), invitation.organizerId(),
                    invitation.organizerName(), koreanTime(invitation.invitedAt()), koreanTime(invitation.expiresAt()));
        }
    }

    /**
     * @param organizerId   닫힌 주최자 id
     * @param organizerName 닫힌 주최자명
     */
    @Schema(description = "다시 열기를 신청할 수 있는 닫힌 주최자 하나")
    public record ReopenableOrganizer(
            @Schema(description = "닫힌 주최자 id", example = "8", requiredMode = REQUIRED)
            long organizerId,

            @Schema(description = "닫힌 주최자명", example = "극단 봄날", requiredMode = REQUIRED)
            String organizerName) {
    }
}
