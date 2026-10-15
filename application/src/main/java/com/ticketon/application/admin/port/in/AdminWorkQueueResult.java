package com.ticketon.application.admin.port.in;

import com.ticketon.application.admin.query.AdminWorkKind;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 관리자 처리 목록.
 *
 * @param items 아직 처리하지 않은 요청 — 접수 일시 순(먼저 들어온 것부터). 없으면 빈 목록.
 */
public record AdminWorkQueueResult(List<Item> items) {

    public AdminWorkQueueResult {
        items = List.copyOf(items);
    }

    /**
     * 처리 목록의 요청 하나.
     *
     * @param kind        종류
     * @param targetId    대상 id — 종류마다 가리키는 것이 다르다(주최자 신청이면 신청서 id, 소유자 변경이면 소유자 변경 이력 id)
     * @param title       제목 — 주최자 신청이면 신청한 이름 또는 단체명, 소유자 변경이면 "주최자명 · 넘긴 사람 → 받은 사람" 닉네임
     * @param receivedAt  접수 일시
     * @param highlighted 강조할지 — 기한이 다가왔다(주최자 신청이면 심사 목표일)
     */
    public record Item(AdminWorkKind kind, long targetId, String title, Instant receivedAt, boolean highlighted) {

        public Item {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(receivedAt, "receivedAt");
        }
    }
}
