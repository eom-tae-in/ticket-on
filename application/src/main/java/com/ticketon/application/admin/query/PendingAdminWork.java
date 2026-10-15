package com.ticketon.application.admin.query;

import java.time.Instant;
import java.util.Objects;

/**
 * 관리자 처리 목록의 아직 처리하지 않은 요청 하나 — 화면에 보일 칸만. 강조할지는 종류마다 기준이 달라 유스케이스가 정한다.
 *
 * @param kind       종류
 * @param targetId   대상 id — 종류마다 가리키는 표가 다르다(주최자 신청이면 신청서 id, 소유자 변경이면 소유자 변경 이력 id, 소유자 이전
 *                   신청이면 신청 id, 주최자명 변경이면 주최자명 변경 이력 id). 누르면 그 대상의 처리 화면으로 간다.
 * @param title      제목 — 목록에서 읽는 한 줄(주최자 신청이면 신청한 이름 또는 단체명, 소유자 변경이면 "주최자명 · 넘긴 사람 → 받은 사람"
 *                   닉네임, 소유자 이전 신청이면 "주최자명 · 신청한 사람" 닉네임, 주최자명 변경이면 "바꾸기 전 이름 → 바꾼 이름")
 * @param receivedAt 접수 일시
 */
public record PendingAdminWork(AdminWorkKind kind, long targetId, String title, Instant receivedAt) {

    public PendingAdminWork {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }
}
