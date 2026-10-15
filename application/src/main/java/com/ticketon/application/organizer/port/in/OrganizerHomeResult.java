package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 내 주최 홈 상태 — 화면이 어느 갈래(소속 없음 · 심사 중 · 주최자 전환)를 보일지 정하는 재료(docs/13 "3. 주최 관리 — 로그인").
 * <p>
 * 심사 중인 신청과 반려된 신청은 그 사람의 <b>가장 최근 신청</b>에서 나온다 — 가장 최근 신청이 심사 중이면 {@code applicationUnderReview},
 * 반려면 {@code rejectedApplication}이 있고, 승인이면 둘 다 없다(소속에 나온다). 다시 신청하면 지난 반려는 보이지 않는다.
 * 신청서의 담당자 연락처 · 사업자 정보 · 정산 계좌는 담지 않는다.
 *
 * @param affiliations           지금 소속된 주최자 — 소속을 시작한 순서. 없으면 빈 목록.
 * @param applicationUnderReview 심사 중인 신청. 없으면 null.
 * @param rejectedApplication    가장 최근 신청이 반려됐으면 그 신청. 아니면 null.
 * @param receivedInvitations    받은 운영자 초대 — 답을 기다리고 유효 기간이 남은 것, 보낸 순서. 없으면 빈 목록.
 * @param reopenableOrganizers   다시 열기를 신청할 수 있는 닫힌 주최자 — 닫기·다시 열기(#134)가 채운다. 없으면 빈 목록.
 */
public record OrganizerHomeResult(List<Affiliation> affiliations, ApplicationUnderReview applicationUnderReview,
        RejectedApplication rejectedApplication, List<ReceivedInvitation> receivedInvitations,
        List<ReopenableOrganizer> reopenableOrganizers) {

    public OrganizerHomeResult {
        affiliations = List.copyOf(affiliations);
        receivedInvitations = List.copyOf(receivedInvitations);
        reopenableOrganizers = List.copyOf(reopenableOrganizers);
        if (applicationUnderReview != null && rejectedApplication != null) {
            throw new IllegalArgumentException("가장 최근 신청은 심사 중이거나 반려거나 둘 중 하나입니다");
        }
    }

    /**
     * 지금 소속된 주최자 하나 — 주최자 전환과 역할에 따른 메뉴 감춤에 쓴다.
     *
     * @param organizerId   주최자 id
     * @param organizerName 주최자명
     * @param role          그 주최자에서의 역할
     * @param since         지금 역할로 소속된 일시
     */
    public record Affiliation(long organizerId, String organizerName, StaffRole role, Instant since) {

        public Affiliation {
            Objects.requireNonNull(organizerName, "organizerName");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(since, "since");
        }
    }

    /**
     * 심사 중인 신청.
     *
     * @param applicationId 신청서 id
     * @param organizerName 신청한 이름 또는 단체명
     * @param submittedAt   제출 일시
     */
    public record ApplicationUnderReview(long applicationId, String organizerName, Instant submittedAt) {

        public ApplicationUnderReview {
            Objects.requireNonNull(organizerName, "organizerName");
            Objects.requireNonNull(submittedAt, "submittedAt");
        }
    }

    /**
     * 반려된 신청 — 사유를 신청한 사람에게 전달한다(docs/01 "반려"). 다시 신청하는 데 제한이 없다.
     *
     * @param applicationId   신청서 id
     * @param organizerName   신청한 이름 또는 단체명
     * @param submittedAt     제출 일시
     * @param rejectedAt      반려 일시
     * @param rejectionReason 반려 사유
     */
    public record RejectedApplication(long applicationId, String organizerName, Instant submittedAt, Instant rejectedAt,
            String rejectionReason) {

        public RejectedApplication {
            Objects.requireNonNull(organizerName, "organizerName");
            Objects.requireNonNull(submittedAt, "submittedAt");
            Objects.requireNonNull(rejectedAt, "rejectedAt");
            Objects.requireNonNull(rejectionReason, "rejectionReason");
        }
    }

    /**
     * 받은 운영자 초대 하나(docs/01 "운영자 초대") — 수락하면 그 주최자의 운영자가 된다.
     *
     * @param invitationId  초대 id — 수락 · 거절할 때 쓴다
     * @param organizerId   초대한 주최자 id
     * @param organizerName 초대한 주최자명
     * @param invitedAt     보낸 일시
     * @param expiresAt     이 순간부터 답할 수 없다
     */
    public record ReceivedInvitation(long invitationId, long organizerId, String organizerName, Instant invitedAt,
            Instant expiresAt) {

        public ReceivedInvitation {
            Objects.requireNonNull(organizerName, "organizerName");
            Objects.requireNonNull(invitedAt, "invitedAt");
            Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    /**
     * 다시 열기를 신청할 수 있는 닫힌 주최자 하나(docs/01 "닫힌 주최자의 소유자였다면 '다시 열기 신청'도 보입니다").
     *
     * @param organizerId   닫힌 주최자 id
     * @param organizerName 닫힌 주최자명
     */
    public record ReopenableOrganizer(long organizerId, String organizerName) {

        public ReopenableOrganizer {
            Objects.requireNonNull(organizerName, "organizerName");
        }
    }
}
