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
     * @param targetId    대상 id — 주최자 신청이면 신청서 id, 소유자 변경이면 소유자 변경 이력 id, 소유자 이전 신청이면 신청 id, 주최자명
     *                    변경이면 주최자명 변경 이력 id, 정산 계좌 · 사업자 정보 변경 확인이면 변경 요청 id
     * @param title       제목
     * @param receivedAt  접수 일시 — 한국 시각
     * @param highlighted 강조할지
     */
    @Schema(description = "처리 목록의 요청 하나")
    public record AdminWorkItem(
            @Schema(description = "종류 — 누르면 종류에 맞는 처리 화면으로 간다(주최자 신청 → 주최자 심사, 소유자 변경 → 사후 확인, 소유자 "
                    + "이전 신청 → 소유자 이전 신청 심사, 주최자명 변경 → 사후 확인, 정산 계좌 · 사업자 정보 변경 확인 → 변경 확인)",
                    requiredMode = REQUIRED)
            AdminWorkKindCode kind,

            @Schema(description = "대상 id — 종류마다 가리키는 것이 다르다. 주최자 신청이면 신청서 id, 소유자 변경이면 소유자 변경 이력 id, "
                    + "소유자 이전 신청이면 신청 id, 주최자명 변경이면 주최자명 변경 이력 id, 정산 계좌 · 사업자 정보 변경 확인이면 변경 요청 id",
                    example = "3",
                    requiredMode = REQUIRED)
            long targetId,

            @Schema(description = "제목 — 주최자 신청이면 신청한 이름 또는 단체명, 소유자 변경이면 \"주최자명 · 넘긴 사람 → 받은 사람\" "
                    + "닉네임, 소유자 이전 신청이면 \"주최자명 · 신청한 사람\" 닉네임, 주최자명 변경이면 \"바꾸기 전 이름 → 바꾼 이름\", "
                    + "정산 계좌 · 사업자 정보 변경 확인이면 \"주최자명 · 정산 계좌 변경\"(또는 \"주최자명 · 사업자 정보 변경\")",
                    example = "극단 열두달", requiredMode = REQUIRED)
            String title,

            @Schema(description = "접수 일시 — 한국 시각(+09:00). 주최자 신청이면 제출 일시, 소유자 변경이면 넘긴 일시, 소유자 이전 신청이면 "
                    + "신청 일시, 주최자명 변경이면 바꾼 일시, 정산 계좌 · 사업자 정보 변경 확인이면 요청 일시",
                    example = "2026-10-15T10:00:00+09:00",
                    requiredMode = REQUIRED)
            OffsetDateTime receivedAt,

            @Schema(description = "강조할지 — 기한이 다가왔다. 주최자 신청이면 심사 목표일(제출일부터 영업일 3일)까지 남은 영업일이 "
                    + "1일 이하이거나 지났다. 소유자 변경 · 소유자 이전 신청 · 주최자명 변경 · 정산 계좌 · 사업자 정보 변경 확인은 기한이 "
                    + "없어 강조하지 않는다",
                    requiredMode = REQUIRED)
            boolean highlighted) {
    }
}
