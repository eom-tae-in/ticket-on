package com.ticketon.application.admin.port.in;

import java.time.Instant;
import java.util.Objects;

/**
 * 주최자 신청 반려 결과.
 *
 * @param applicationId   반려한 신청서 id
 * @param organizerName   신청한 이름 또는 단체명
 * @param rejectionReason 신청한 사람에게 전달한 반려 사유
 * @param rejectedAt      반려 일시
 */
public record RejectOrganizerApplicationResult(long applicationId, String organizerName, String rejectionReason,
        Instant rejectedAt) {

    public RejectOrganizerApplicationResult {
        Objects.requireNonNull(organizerName, "organizerName");
        Objects.requireNonNull(rejectionReason, "rejectionReason");
        Objects.requireNonNull(rejectedAt, "rejectedAt");
    }
}
