package com.ticketon.application.organizer.query;

import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.util.Objects;

/**
 * 주최자 이력의 운영진 조작 기록 한 줄 — 누가 · 언제 · 무엇을. 이름은 지금 운영진 로그인의 것이다(닉네임을 바꿔도 기록은 그 사람을 따라간다).
 *
 * @param recordId       기록 id — 넣은 순서
 * @param operatedAt     조작 일시
 * @param staffAccountId 조작한 운영진의 계정 id
 * @param staffNickname  조작한 운영진의 지금 닉네임
 * @param staffName      조작한 운영진의 이름(가입 때 받은 실명)
 * @param staffRole      조작할 때의 역할
 * @param action         조작 종류 — 그 조작으로 한 일
 * @param targetKind     대상 종류
 * @param targetId       대상 id
 * @param summary        요약 — "무엇을" 한 줄
 */
public record StaffOperationHistoryEntry(long recordId, Instant operatedAt, long staffAccountId, String staffNickname,
        String staffName, StaffRole staffRole, StaffAction action, StaffOperationTargetKind targetKind, long targetId,
        String summary) {

    public StaffOperationHistoryEntry {
        Objects.requireNonNull(operatedAt, "operatedAt");
        Objects.requireNonNull(staffNickname, "staffNickname");
        Objects.requireNonNull(staffName, "staffName");
        Objects.requireNonNull(staffRole, "staffRole");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetKind, "targetKind");
        Objects.requireNonNull(summary, "summary");
    }
}
