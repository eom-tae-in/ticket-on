package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.StaffAction;
import com.ticketon.domain.organizer.StaffOperationTargetKind;
import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 주최자 이력의 운영진 조작 기록 — 최근 것부터. 목록에서는 "초대권 10장 발권 · 운영자 김○○"처럼 요약 뒤에 그때의 역할과 사람이 붙는다
 * (docs/01 "누가 했는지 남깁니다").
 *
 * @param entries            기록 — 최근 것부터
 * @param nextBeforeRecordId 더 앞의 기록이 있으면 이어 보기에 넘길 값. 없으면 null.
 */
public record StaffOperationHistoryResult(List<Entry> entries, Long nextBeforeRecordId) {

    public StaffOperationHistoryResult {
        entries = List.copyOf(entries);
    }

    /**
     * 기록 한 줄. 사람의 이름은 지금 운영진 로그인의 것이다 — 닉네임을 바꿔도 기록은 그 사람을 따라간다.
     *
     * @param recordId       기록 id
     * @param operatedAt     조작 일시
     * @param staffAccountId 조작한 운영진의 계정 id
     * @param staffNickname  조작한 운영진의 지금 닉네임
     * @param staffName      조작한 운영진의 이름(가입 때 받은 실명). 운영진 로그인을 탈퇴해 이름을 파기했으면 null — 닉네임은 남는다.
     * @param staffRole      조작할 때의 역할
     * @param action         조작 종류 — 그 조작으로 한 일
     * @param targetKind     대상 종류
     * @param targetId       대상 id
     * @param summary        요약 — "무엇을" 한 줄
     */
    public record Entry(long recordId, Instant operatedAt, long staffAccountId, String staffNickname, String staffName,
            StaffRole staffRole, StaffAction action, StaffOperationTargetKind targetKind, long targetId,
            String summary) {

        public Entry {
            Objects.requireNonNull(operatedAt, "operatedAt");
            Objects.requireNonNull(staffNickname, "staffNickname");
            Objects.requireNonNull(staffRole, "staffRole");
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(targetKind, "targetKind");
            Objects.requireNonNull(summary, "summary");
        }
    }
}
