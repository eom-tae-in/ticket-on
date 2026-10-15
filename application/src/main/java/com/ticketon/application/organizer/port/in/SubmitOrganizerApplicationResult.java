package com.ticketon.application.organizer.port.in;

import java.time.Instant;
import java.util.Objects;

/**
 * 주최자 신청서 제출 결과 — 낸 신청은 심사 중이다. 화면은 이것으로 "심사 중" 화면을 보인다(docs/01 "승인 전까지 '심사 중' 화면만").
 * 담당자 연락처 · 사업자 정보 · 정산 계좌는 돌려주지 않는다.
 *
 * @param applicationId 신청서 id
 * @param organizerName 신청한 이름 또는 단체명
 * @param submittedAt   제출 일시
 */
public record SubmitOrganizerApplicationResult(long applicationId, String organizerName, Instant submittedAt) {

    public SubmitOrganizerApplicationResult {
        Objects.requireNonNull(organizerName, "organizerName");
        Objects.requireNonNull(submittedAt, "submittedAt");
    }
}
