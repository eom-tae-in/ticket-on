package com.ticketon.application.admin.port.in;

import com.ticketon.domain.organizer.OwnershipClaimStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 소유자 이전 신청 심사 화면 — 신청과, 관리자가 확인하는 데 쓰는 재료(docs/11 "대표가 바뀔 때 — 관리자는 주최자 신청 때 받은 연락처와 SNS 등으로
 * 확인한 뒤 소유자를 바꿉니다").
 * <p>
 * 주최자 신청 때 받은 담당자 연락처 · 이메일을 담으므로 {@link #toString}에는 신청 id · 주최자명 · 상태만 싣는다 — 로그로 새지 않게.
 *
 * @param ownershipClaimId   신청 id
 * @param organizerId        주최자 id
 * @param organizerName      주최자명
 * @param status             신청 상태 — 심사 중일 때만 승인·반려할 수 있다
 * @param reason             신청 사유 — 소유자와 연락이 끊긴 사정
 * @param submittedAt        신청 일시
 * @param reviewedAt         승인·반려 일시. 심사 중이면 null.
 * @param rejectionReason    반려 사유. 반려가 아니면 null.
 * @param claimant           신청한 운영자 — 승인되면 소유자가 된다
 * @param claimantIsOperator 신청한 사람이 지금도 그 주최자의 운영자인지 — 아니면(나갔거나 탈퇴했으면) 승인할 수 없다
 * @param ownerAtSubmission  신청 때의 소유자 — 연락이 끊겼다는 그 사람. 승인되면 운영자로 남는다
 * @param currentOwner       지금 소유자. 신청 때의 소유자와 다르면 그 사이 소유자가 바뀐 것이라 승인할 수 없다. 운영진이 남지 않았으면 null.
 * @param applicationContact 주최자 신청 때 받은 연락처 · SNS — 그 주최자의 가장 최근 승인된 신청서. 없으면 null.
 */
public record OwnershipClaimReviewResult(long ownershipClaimId, long organizerId, String organizerName,
        OwnershipClaimStatus status, String reason, Instant submittedAt, Instant reviewedAt, String rejectionReason,
        StaffMember claimant, boolean claimantIsOperator, StaffMember ownerAtSubmission, StaffMember currentOwner,
        ApplicationContact applicationContact) {

    public OwnershipClaimReviewResult {
        Objects.requireNonNull(organizerName, "organizerName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(submittedAt, "submittedAt");
        Objects.requireNonNull(claimant, "claimant");
        Objects.requireNonNull(ownerAtSubmission, "ownerAtSubmission");
    }

    @Override
    public String toString() {
        return "OwnershipClaimReviewResult[ownershipClaimId=" + ownershipClaimId + ", organizerName=" + organizerName
                + ", status=" + status + "]";
    }

    /**
     * 운영진 한 사람 — 공개 프로필의 닉네임으로 적는다(탈퇴해도 닉네임은 남는다).
     *
     * @param staffAccountId 운영진 계정 id
     * @param nickname       닉네임
     */
    public record StaffMember(long staffAccountId, String nickname) {

        public StaffMember {
            Objects.requireNonNull(nickname, "nickname");
        }
    }

    /**
     * 주최자 신청 때 받은 연락처 · SNS(docs/01 "신청 항목" — 담당자 연락처는 실제로 전화해 봄, 홈페이지·SNS 링크는 실재 확인의 핵심).
     *
     * @param organizerApplicationId 그 신청서 id — 주최자 심사 화면에서 신청서 전체를 볼 수 있다
     * @param contactPhoneNumber     담당자 연락처
     * @param email                  이메일
     * @param websiteAndSnsLinks     홈페이지·SNS 링크 — 적은 순서. 없으면 빈 목록.
     */
    public record ApplicationContact(long organizerApplicationId, String contactPhoneNumber, String email,
            List<String> websiteAndSnsLinks) {

        public ApplicationContact {
            Objects.requireNonNull(contactPhoneNumber, "contactPhoneNumber");
            Objects.requireNonNull(email, "email");
            websiteAndSnsLinks = List.copyOf(websiteAndSnsLinks);
        }

        @Override
        public String toString() {
            return "ApplicationContact[organizerApplicationId=" + organizerApplicationId + ", ****]";
        }
    }
}
