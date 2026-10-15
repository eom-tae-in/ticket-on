package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.StaffOperationTargetKind;
import java.time.Instant;
import java.util.Objects;

/**
 * 운영진 조작 기록 입력.
 *
 * @param permission 조작 전에 확인한 권한({@link CheckStaffPermissionUseCase}) — 주최자 · 운영진 · 그때의 역할 · 한 일이 여기서 온다
 * @param targetKind 대상 종류
 * @param targetId   대상 id
 * @param summary    요약 — 목록에서 읽는 "무엇을" 한 줄(예: "초대권 10장 발권")
 * @param operatedAt 조작 일시 — 조작에 쓴 시각 그대로
 */
public record RecordStaffOperationCommand(StaffPermissionResult permission, StaffOperationTargetKind targetKind, long targetId,
        String summary, Instant operatedAt) {

    public RecordStaffOperationCommand {
        Objects.requireNonNull(permission, "permission");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(operatedAt, "operatedAt");
    }
}
