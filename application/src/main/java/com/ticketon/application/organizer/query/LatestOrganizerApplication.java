package com.ticketon.application.organizer.query;

import com.ticketon.domain.organizer.OrganizerApplicationStatus;
import java.time.Instant;
import java.util.Objects;

/**
 * 한 사람이 가장 최근에 낸 주최자 신청 — 주최 홈에 보일 칸만. 담당자 연락처 · 사업자 정보 · 정산 계좌는 싣지 않는다.
 *
 * @param applicationId   신청서 id
 * @param organizerName   신청한 이름 또는 단체명
 * @param status          신청서 상태
 * @param submittedAt     제출 일시
 * @param reviewedAt      승인·반려 일시. 심사 중이면 null.
 * @param rejectionReason 반려 사유. 반려가 아니면 null.
 */
public record LatestOrganizerApplication(long applicationId, String organizerName, OrganizerApplicationStatus status,
        Instant submittedAt, Instant reviewedAt, String rejectionReason) {

    public LatestOrganizerApplication {
        Objects.requireNonNull(organizerName, "organizerName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(submittedAt, "submittedAt");
        if (status == OrganizerApplicationStatus.REJECTED && (reviewedAt == null || rejectionReason == null)) {
            throw new IllegalArgumentException("반려된 신청에는 반려 일시와 사유가 있습니다: 신청서 " + applicationId);
        }
    }
}
