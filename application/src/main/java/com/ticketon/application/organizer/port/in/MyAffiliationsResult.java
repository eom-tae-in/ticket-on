package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.OrganizerStatus;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 내 소속 목록 — 주최자 전환에서 고를 주최자들(docs/01 "한 사람이 여러 주최자에 소속될 수 있습니다 … 주최 관리 화면에 주최자를 바꾸는 메뉴가
 * 생깁니다", docs/13 "3. 주최 관리 — 여러 곳에 소속돼 있으면 → 주최자 전환").
 *
 * @param affiliations 지금 소속된 주최자 — 소속을 시작한 순서. 없으면 빈 목록.
 */
public record MyAffiliationsResult(List<Affiliation> affiliations) {

    public MyAffiliationsResult {
        affiliations = List.copyOf(affiliations);
    }

    /**
     * 지금 소속된 주최자 하나.
     *
     * @param organizerId     주최자 id — 고르면 주최 관리 요청의 경로에 쓴다
     * @param organizerName   주최자명
     * @param role            그 주최자에서의 역할 — 운영자에게는 소유자 전용 메뉴를 감춘다
     * @param organizerStatus 그 주최자의 상태
     * @param since           지금 역할로 소속된 일시
     */
    public record Affiliation(long organizerId, String organizerName, StaffRole role, OrganizerStatus organizerStatus,
            Instant since) {

        public Affiliation {
            Objects.requireNonNull(organizerName, "organizerName");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(organizerStatus, "organizerStatus");
            Objects.requireNonNull(since, "since");
        }
    }
}
