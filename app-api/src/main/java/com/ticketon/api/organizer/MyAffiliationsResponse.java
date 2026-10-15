package com.ticketon.api.organizer;

import static com.ticketon.api.organizer.OrganizerApplicationUnderReviewResponse.koreanTime;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.organizer.port.in.MyAffiliationsResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 내 소속 목록 — 주최자 전환에서 고를 주최자들(docs/13 "3. 주최 관리 — 여러 곳에 소속돼 있으면 → 주최자 전환").
 *
 * @param affiliations 지금 소속된 주최자
 */
@Schema(description = "내 소속 목록 — 주최자 전환")
public record MyAffiliationsResponse(
        @Schema(description = "지금 소속된 주최자 — 소속을 시작한 순서. 고른 주최자의 id로 주최 관리 경로(/api/organizer/organizers/{organizerId})를 "
                + "부른다. 없으면 빈 목록", requiredMode = REQUIRED)
        List<MyAffiliation> affiliations) {

    static MyAffiliationsResponse from(MyAffiliationsResult result) {
        return new MyAffiliationsResponse(result.affiliations().stream()
                .map(affiliation -> new MyAffiliation(affiliation.organizerId(), affiliation.organizerName(),
                        StaffRoleCode.from(affiliation.role()), OrganizerStatusCode.from(affiliation.organizerStatus()),
                        koreanTime(affiliation.since())))
                .toList());
    }

    /**
     * @param organizerId     주최자 id
     * @param organizerName   주최자명
     * @param role            그 주최자에서의 역할
     * @param organizerStatus 그 주최자의 상태
     * @param since           지금 역할로 소속된 일시 — 한국 시각
     */
    @Schema(description = "지금 소속된 주최자 하나")
    public record MyAffiliation(
            @Schema(description = "주최자 id", example = "12", requiredMode = REQUIRED)
            long organizerId,

            @Schema(description = "주최자명", example = "극단 열두달", requiredMode = REQUIRED)
            String organizerName,

            @Schema(description = "그 주최자에서의 역할 — 운영자에게는 소유자 전용 메뉴를 감춘다", requiredMode = REQUIRED)
            StaffRoleCode role,

            @Schema(description = "그 주최자의 상태 — 지금 소속은 활동 중인 주최자에만 남는다(닫히거나 영구 정지되면 운영진이 빠진다)",
                    requiredMode = REQUIRED)
            OrganizerStatusCode organizerStatus,

            @Schema(description = "지금 역할로 소속된 일시 — 한국 시각(+09:00)", example = "2026-10-17T15:00:00+09:00",
                    requiredMode = REQUIRED)
            OffsetDateTime since) {
    }
}
