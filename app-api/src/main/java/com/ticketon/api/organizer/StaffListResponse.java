package com.ticketon.api.organizer;

import static com.ticketon.api.organizer.OrganizerApplicationUnderReviewResponse.koreanTime;
import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.organizer.port.in.StaffListResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 운영진 목록 — 운영진 관리 화면(docs/13 "운영진 관리 — 운영진 목록 (소유자 · 운영자)"). 실명 · 이메일은 담지 않는다.
 *
 * @param members 지금 운영진 — 소유자가 먼저
 */
@Schema(description = "운영진 목록")
public record StaffListResponse(
        @Schema(description = "지금 운영진 — 소유자가 먼저, 나머지는 지금 역할로 소속된 순서. 나간 사람은 빠진다", requiredMode = REQUIRED)
        List<StaffMember> members) {

    static StaffListResponse from(StaffListResult result) {
        return new StaffListResponse(result.members().stream()
                .map(member -> new StaffMember(member.staffAccountId(), member.nickname(),
                        StaffRoleCode.from(member.role()), koreanTime(member.since())))
                .toList());
    }

    /**
     * @param staffAccountId 운영진 계정 id
     * @param nickname       닉네임
     * @param role           그 주최자에서의 역할
     * @param since          지금 역할로 소속된 일시 — 한국 시각
     */
    @Schema(description = "운영진 한 사람")
    public record StaffMember(
            @Schema(description = "운영진 계정 id — 내보낼 때 쓴다", example = "4", requiredMode = REQUIRED)
            long staffAccountId,

            @Schema(description = "닉네임 — 실명 대신 보인다", example = "푸른바람", requiredMode = REQUIRED)
            String nickname,

            @Schema(description = "그 주최자에서의 역할", requiredMode = REQUIRED)
            StaffRoleCode role,

            @Schema(description = "지금 역할로 소속된 일시 — 한국 시각(+09:00)", example = "2026-10-16T21:00:00+09:00",
                    requiredMode = REQUIRED)
            OffsetDateTime since) {
    }
}
