package com.ticketon.domain.organizer;

import java.time.Instant;
import java.util.Objects;

/**
 * 운영진 조작 기록 한 줄 — 어느 주최자에서 · 누가(운영진과 그때의 역할) · 언제 · 무엇을(조작 종류) · 무엇에(대상 종류·id) · 요약.
 * <p>
 * <b>운영진의 모든 조작에 남긴다</b>(docs/01 "권한 두 가지 — 누가 했는지 남깁니다"). 기록은 주최자에 걸린다 — 관리자는 주최자 상세에서 그
 * 주최자의 운영진이 한 조작을 본다(docs/13 "5. 플랫폼 관리자 — 모든 조작에 '누가 언제 무엇을'이 기록됩니다").
 * 조작과 같은 트랜잭션에서 남겨, 조작이 이뤄졌는데 기록이 없는 일이 없게 한다(application의 운영진 조작 기록 유스케이스).
 * <ul>
 *   <li><b>누가</b> — 조작 전에 확인한 권한({@link StaffPermission})에서 온다. 주최자 · 운영진 · 그때의 역할 · 한 일이 확인한 그대로 남는다.
 *       역할은 나중에 바뀔 수 있어(소유자 넘기기) 조작할 때의 역할을 남긴다. 사람은 운영진 계정 id로 가리킨다 — 닉네임을 바꿔도 기록은
 *       그 사람을 따라간다.</li>
 *   <li><b>조작 종류</b> — 그 조작으로 한 일({@link StaffAction}). 권한 표의 일 그대로다. 같은 일 안의 세부(발권인지 회수인지 등)는 요약이
 *       말한다.</li>
 *   <li><b>요약</b> — 목록에서 읽는 "무엇을" 한 줄이다(예: "초대권 10장 발권"). 누가 했는지는 따로 남아, 목록에서
 *       "초대권 10장 발권 · 운영자 김○○"처럼 붙는다(docs/01).</li>
 * </ul>
 * 보관 기간은 문서에 정해져 있지 않아 지우지 않는다(docs/11 "데이터 보관"에 없음 — 정해지면 파기 작업을 더한다).
 */
public final class StaffOperationRecord {

    private final StaffPermission permission;
    private final StaffOperationTargetKind targetKind;
    private final long targetId;
    private final String summary;
    private final Instant operatedAt;

    private StaffOperationRecord(StaffPermission permission, StaffOperationTargetKind targetKind, long targetId,
            String summary, Instant operatedAt) {
        this.permission = permission;
        this.targetKind = targetKind;
        this.targetId = targetId;
        this.summary = summary;
        this.operatedAt = operatedAt;
    }

    /**
     * 조작 기록 한 줄을 만든다.
     *
     * @param permission 조작 전에 확인한 권한 — 주최자 · 운영진 · 역할 · 한 일
     * @param targetKind 대상 종류
     * @param targetId   대상 id (1 이상). 대상이 주최자 자신이면 기록이 걸린 주최자의 id여야 한다.
     * @param summary    요약 — 앞뒤 공백을 걷고, 비어 있으면 안 된다
     * @param operatedAt 조작 일시
     * @throws IllegalArgumentException 1보다 작은 대상 id, 다른 주최자를 대상으로 한 주최자 조작, 빈 요약
     */
    public static StaffOperationRecord of(StaffPermission permission, StaffOperationTargetKind targetKind, long targetId,
            String summary, Instant operatedAt) {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(operatedAt, "operatedAt");
        if (targetId < 1) {
            throw new IllegalArgumentException("대상 id는 1 이상이어야 합니다: " + targetId);
        }
        if (targetKind == StaffOperationTargetKind.ORGANIZER && targetId != permission.organizerId()) {
            throw new IllegalArgumentException(
                    "주최자 " + permission.organizerId() + "의 기록에 다른 주최자(" + targetId + ")를 대상으로 남기려 했습니다");
        }
        String strippedSummary = summary.strip();
        if (strippedSummary.isEmpty()) {
            throw new IllegalArgumentException("운영진 조작 기록의 요약이 비어 있습니다");
        }
        return new StaffOperationRecord(permission, targetKind, targetId, strippedSummary, operatedAt);
    }

    /** 기록이 걸린 주최자의 id. */
    public long organizerId() {
        return permission.organizerId();
    }

    /** 조작한 운영진의 계정 id. */
    public long staffAccountId() {
        return permission.staffAccountId();
    }

    /** 조작할 때의 역할. */
    public StaffRole staffRole() {
        return permission.role();
    }

    /** 조작 종류 — 그 조작으로 한 일. */
    public StaffAction action() {
        return permission.action();
    }

    public StaffOperationTargetKind targetKind() {
        return targetKind;
    }

    public long targetId() {
        return targetId;
    }

    public String summary() {
        return summary;
    }

    public Instant operatedAt() {
        return operatedAt;
    }
}
