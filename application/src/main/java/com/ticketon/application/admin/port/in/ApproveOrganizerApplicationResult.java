package com.ticketon.application.admin.port.in;

import java.time.Instant;
import java.util.Objects;

/**
 * 주최자 신청 승인 결과.
 *
 * @param applicationId       승인한 신청서 id
 * @param organizerId         활동이 된 주최자 id
 * @param organizerName       주최자명 — 신청한 이름 또는 단체명
 * @param ownerStaffAccountId 소유자가 된 사람(신청한 사람)의 운영진 계정 id
 * @param approvedAt          승인 일시
 */
public record ApproveOrganizerApplicationResult(long applicationId, long organizerId, String organizerName,
        long ownerStaffAccountId, Instant approvedAt) {

    public ApproveOrganizerApplicationResult {
        Objects.requireNonNull(organizerName, "organizerName");
        Objects.requireNonNull(approvedAt, "approvedAt");
    }
}
