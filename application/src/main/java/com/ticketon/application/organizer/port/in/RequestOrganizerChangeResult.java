package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.OrganizerChangeRequestKind;
import java.time.Instant;
import java.util.Objects;

/**
 * 낸 변경 요청 — 관리자 확인 중으로 시작한다. 결과(승인 · 반려)는 소유자에게 메일로 간다.
 *
 * @param changeRequestId 변경 요청 id
 * @param kind            바꾸려는 것의 종류
 * @param requestedAt     요청 일시
 */
public record RequestOrganizerChangeResult(long changeRequestId, OrganizerChangeRequestKind kind, Instant requestedAt) {

    public RequestOrganizerChangeResult {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
