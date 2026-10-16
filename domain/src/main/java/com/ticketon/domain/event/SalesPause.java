package com.ticketon.domain.event;

import java.time.Instant;
import java.util.Objects;

/**
 * 판매 중단 이력 한 건 — 누가 · 언제 · 왜 멈췄고 언제 끝났는지(docs/04 "'중단했던 이력'은 따로 남깁니다").
 * <p>
 * <b>상태가 덮여도 남는다.</b> 판매 중단 중에 예매 종료 시각이 지나 판매 종료가 돼도, 행사가 끝나도 이력은 그대로다 — 관리자가 판단하려면 기록이
 * 남아야 한다. 판매 종료로 넘어가 재개하지 못한 중단은 끝난 시각이 비어 있다.
 * <ul>
 *   <li><b>주최자 중단</b> — 운영진이 멈췄다. 한 줄 사유(선택)가 있을 수 있다. 운영진이 재개하면 끝난다.</li>
 *   <li><b>관리자 조치</b>(정지 2단계 · 페이지 내리기) — 관리자가 걸었다. 사유는 정지 기록 · 페이지 내리기가 갖는다. 관리자가 조치를 풀면 끝난다 —
 *       판매는 저절로 열리지 않으므로 조치가 끝나도 판매 중단은 이어질 수 있다.</li>
 * </ul>
 *
 * @param cause          멈춘 까닭
 * @param actorAccountId 멈춘 사람 — 주최자 중단은 운영진 계정 id, 관리자 조치는 관리자 계정 id
 * @param reason         주최자가 적은 한 줄 사유. 적지 않았거나 관리자 조치면 null
 * @param pausedAt       멈춘 시각
 * @param endedAt        끝난 시각 — 주최자 중단은 재개한 시각, 관리자 조치는 조치를 푼 시각. 아직 끝나지 않았으면 null
 */
public record SalesPause(SalesPauseCause cause, long actorAccountId, SalesPauseReason reason, Instant pausedAt, Instant endedAt) {

    public SalesPause {
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(pausedAt, "pausedAt");
        if (actorAccountId < 1) {
            throw new IllegalArgumentException("멈춘 사람의 계정 id는 1 이상이어야 합니다: " + actorAccountId);
        }
        if (reason != null && cause.isAdminAction()) {
            throw new IllegalArgumentException("관리자 조치의 판매 중단은 사유를 갖지 않습니다: " + cause);
        }
        if (endedAt != null && endedAt.isBefore(pausedAt)) {
            throw new IllegalArgumentException("판매 중단이 멈춘 시각보다 먼저 끝났습니다: " + pausedAt + " ~ " + endedAt);
        }
    }

    /** 운영진이 지금 멈춘 판매 중단. */
    static SalesPause byOrganizer(long staffAccountId, SalesPauseReason reason, Instant pausedAt) {
        return new SalesPause(SalesPauseCause.ORGANIZER, staffAccountId, reason, pausedAt, null);
    }

    /** 관리자가 지금 건 조치의 판매 중단. */
    static SalesPause byAdmin(SalesPauseCause cause, long adminAccountId, Instant pausedAt) {
        if (!cause.isAdminAction()) {
            throw new IllegalArgumentException("관리자 조치가 아닙니다: " + cause);
        }
        return new SalesPause(cause, adminAccountId, null, pausedAt, null);
    }

    /** 아직 끝나지 않았는지. */
    public boolean isActive() {
        return endedAt == null;
    }

    /** 그 시각에 끝난 이력. */
    SalesPause end(Instant at) {
        Objects.requireNonNull(at, "at");
        if (!isActive()) {
            throw new IllegalStateException("이미 끝난 판매 중단입니다: " + endedAt);
        }
        return new SalesPause(cause, actorAccountId, reason, pausedAt, at);
    }
}
