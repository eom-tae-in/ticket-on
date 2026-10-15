package com.ticketon.api.admin;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.NOT_REQUIRED;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.admin.port.in.OwnershipClaimReviewResult;
import com.ticketon.common.KoreanTime;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 소유자 이전 신청 심사 화면(docs/13 "5. 플랫폼 관리자 — 처리 목록: 소유자 이전 신청"). 관리자는 신청 사유와 주최자 신청 때 받은 연락처 · SNS
 * 등으로 확인한 뒤 승인하거나 반려한다(docs/11 "대표가 바뀔 때"). 문서에 정한 체크리스트 · 증빙은 없다.
 * <p>
 * 주최자 신청 때 받은 담당자 연락처 · 이메일이 있어 {@link #toString}에는 싣지 않는다.
 *
 * @param ownershipClaimId   신청 id
 * @param organizerId        주최자 id
 * @param organizerName      주최자명
 * @param status             신청 상태
 * @param reason             신청 사유
 * @param submittedAt        신청 일시 — 한국 시각
 * @param reviewedAt         승인·반려 일시 — 한국 시각. 심사 중이면 null.
 * @param rejectionReason    반려 사유. 반려가 아니면 null.
 * @param claimant           신청한 운영자
 * @param claimantIsOperator 신청한 사람이 지금도 운영자인지
 * @param ownerAtSubmission  신청 때의 소유자
 * @param currentOwner       지금 소유자. 운영진이 남지 않았으면 null.
 * @param applicationContact 주최자 신청 때 받은 연락처 · SNS. 승인된 신청서가 없으면 null.
 */
@Schema(description = "소유자 이전 신청 심사 화면")
public record OwnershipClaimReviewResponse(
        @Schema(description = "신청 id", example = "4", requiredMode = REQUIRED)
        long ownershipClaimId,

        @Schema(description = "주최자 id", example = "12", requiredMode = REQUIRED)
        long organizerId,

        @Schema(description = "주최자명", example = "극단 열두달", requiredMode = REQUIRED)
        String organizerName,

        @Schema(description = "신청 상태 — 심사 중일 때만 승인·반려할 수 있다", requiredMode = REQUIRED)
        OwnershipClaimStatusCode status,

        @Schema(description = "신청 사유 — 소유자와 연락이 끊긴 사정", example = "전 대표가 졸업한 뒤 전화·메일 모두 답이 없습니다.",
                requiredMode = REQUIRED)
        String reason,

        @Schema(description = "신청 일시 — 한국 시각(+09:00)", example = "2026-10-15T10:00:00+09:00", requiredMode = REQUIRED)
        OffsetDateTime submittedAt,

        @Schema(description = "승인·반려 일시 — 한국 시각(+09:00). 심사 중이면 null", example = "2026-10-16T15:00:00+09:00",
                requiredMode = NOT_REQUIRED, nullable = true)
        OffsetDateTime reviewedAt,

        @Schema(description = "반려 사유 — 반려가 아니면 null", example = "소유자와 통화해 직접 넘기기로 함", requiredMode = NOT_REQUIRED,
                nullable = true)
        String rejectionReason,

        @Schema(description = "신청한 운영자 — 승인되면 소유자가 된다", requiredMode = REQUIRED)
        ClaimStaffMember claimant,

        @Schema(description = "신청한 사람이 지금도 그 주최자의 운영자인지 — 아니면(나갔거나 탈퇴했으면) 승인할 수 없고 반려한다",
                example = "true", requiredMode = REQUIRED)
        boolean claimantIsOperator,

        @Schema(description = "신청 때의 소유자 — 연락이 끊겼다는 그 사람. 승인되면 운영자로 남는다", requiredMode = REQUIRED)
        ClaimStaffMember ownerAtSubmission,

        @Schema(description = "지금 소유자 — 신청 때의 소유자와 다르면 그 사이 소유자가 바뀐 것이라 승인할 수 없고 반려한다. 운영진이 남지 "
                + "않았으면 null", requiredMode = NOT_REQUIRED, nullable = true)
        ClaimStaffMember currentOwner,

        @Schema(description = "주최자 신청 때 받은 연락처 · SNS — 그 주최자의 가장 최근 승인된 신청서. 없으면 null",
                requiredMode = NOT_REQUIRED, nullable = true)
        ClaimApplicationContact applicationContact) {

    static OwnershipClaimReviewResponse from(OwnershipClaimReviewResult result) {
        OwnershipClaimReviewResult.ApplicationContact contact = result.applicationContact();
        return new OwnershipClaimReviewResponse(
                result.ownershipClaimId(),
                result.organizerId(),
                result.organizerName(),
                OwnershipClaimStatusCode.from(result.status()),
                result.reason(),
                koreanTime(result.submittedAt()),
                result.reviewedAt() == null ? null : koreanTime(result.reviewedAt()),
                result.rejectionReason(),
                ClaimStaffMember.from(result.claimant()),
                result.claimantIsOperator(),
                ClaimStaffMember.from(result.ownerAtSubmission()),
                result.currentOwner() == null ? null : ClaimStaffMember.from(result.currentOwner()),
                contact == null ? null : new ClaimApplicationContact(contact.organizerApplicationId(),
                        contact.contactPhoneNumber(), contact.email(), contact.websiteAndSnsLinks()));
    }

    private static OffsetDateTime koreanTime(Instant instant) {
        return instant.atZone(KoreanTime.ZONE).toOffsetDateTime();
    }

    @Override
    public String toString() {
        return "OwnershipClaimReviewResponse[ownershipClaimId=" + ownershipClaimId + ", organizerName=" + organizerName
                + ", status=" + status + "]";
    }

    /**
     * @param staffAccountId 운영진 계정 id
     * @param nickname       닉네임 — 공개 프로필의 이름
     */
    @Schema(description = "소유자 이전 신청에 걸린 운영진 한 사람")
    public record ClaimStaffMember(
            @Schema(description = "운영진 계정 id", example = "5", requiredMode = REQUIRED)
            long staffAccountId,

            @Schema(description = "닉네임 — 공개 프로필의 이름", example = "푸른바람", requiredMode = REQUIRED)
            String nickname) {

        static ClaimStaffMember from(OwnershipClaimReviewResult.StaffMember member) {
            return new ClaimStaffMember(member.staffAccountId(), member.nickname());
        }
    }

    /**
     * @param organizerApplicationId 그 신청서 id — 주최자 심사 화면에서 신청서 전체를 본다
     * @param contactPhoneNumber     담당자 연락처
     * @param email                  이메일
     * @param websiteAndSnsLinks     홈페이지·SNS 링크
     */
    @Schema(description = "주최자 신청 때 받은 연락처 · SNS")
    public record ClaimApplicationContact(
            @Schema(description = "그 신청서 id — 주최자 심사 화면(/api/admin/organizer-applications/{applicationId})에서 신청서 전체를 본다",
                    example = "3", requiredMode = REQUIRED)
            long organizerApplicationId,

            @Schema(description = "담당자 연락처 — 적은 그대로", example = "010-2345-6789", requiredMode = REQUIRED)
            String contactPhoneNumber,

            @Schema(description = "이메일", example = "twelve-months@example.com", requiredMode = REQUIRED)
            String email,

            @ArraySchema(arraySchema = @Schema(description = "홈페이지·SNS 링크 — 적은 순서. 없으면 빈 목록", requiredMode = REQUIRED),
                    schema = @Schema(example = "https://www.instagram.com/twelve_months_theater"))
            List<String> websiteAndSnsLinks) {

        @Override
        public String toString() {
            return "ClaimApplicationContact[organizerApplicationId=" + organizerApplicationId + ", ****]";
        }
    }
}
