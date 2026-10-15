package com.ticketon.api.admin;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import com.ticketon.application.admin.port.in.AdminWorkQueueResult;
import com.ticketon.common.KoreanTime;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 관리자 처리 목록 — 아직 처리하지 않은 요청이 종류와 상관없이 한곳에 접수 순서로 쌓인다(docs/13 "5. 플랫폼 관리자 — 처리 목록").
 *
 * @param items 아직 처리하지 않은 요청 — 접수 순서
 */
@Schema(description = "관리자 처리 목록")
public record AdminWorkQueueResponse(
        @Schema(description = "아직 처리하지 않은 요청 — 접수 일시 순(먼저 들어온 것부터). 없으면 빈 목록", requiredMode = REQUIRED)
        List<AdminWorkItem> items) {

    static AdminWorkQueueResponse from(AdminWorkQueueResult result) {
        return new AdminWorkQueueResponse(result.items().stream()
                .map(item -> new AdminWorkItem(AdminWorkKindCode.from(item.kind()), item.targetId(), item.title(),
                        item.receivedAt().atZone(KoreanTime.ZONE).toOffsetDateTime(), item.highlighted()))
                .toList());
    }

    /**
     * @param kind        종류
     * @param targetId    대상 id — 주최자 신청이면 신청서 id
     * @param title       제목
     * @param receivedAt  접수 일시 — 한국 시각
     * @param highlighted 강조할지
     */
    @Schema(description = "처리 목록의 요청 하나")
    public record AdminWorkItem(
            @Schema(description = "종류 — 누르면 종류에 맞는 처리 화면으로 간다(주최자 신청 → 주최자 심사)", requiredMode = REQUIRED)
            AdminWorkKindCode kind,

            @Schema(description = "대상 id — 종류마다 가리키는 것이 다르다. 주최자 신청이면 신청서 id", example = "3",
                    requiredMode = REQUIRED)
            long targetId,

            @Schema(description = "제목 — 주최자 신청이면 신청한 이름 또는 단체명", example = "극단 열두달", requiredMode = REQUIRED)
            String title,

            @Schema(description = "접수 일시 — 한국 시각(+09:00). 주최자 신청이면 제출 일시", example = "2026-10-15T10:00:00+09:00",
                    requiredMode = REQUIRED)
            OffsetDateTime receivedAt,

            @Schema(description = "강조할지 — 기한이 다가왔다. 주최자 신청이면 심사 목표일(제출일부터 영업일 3일)까지 남은 영업일이 "
                    + "1일 이하이거나 지났다", requiredMode = REQUIRED)
            boolean highlighted) {
    }
}
