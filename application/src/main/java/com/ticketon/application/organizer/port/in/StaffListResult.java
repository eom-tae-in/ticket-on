package com.ticketon.application.organizer.port.in;

import com.ticketon.domain.organizer.StaffRole;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 한 주최자의 지금 운영진 목록 — 소유자가 먼저(docs/13 "운영진 관리 — 운영진 목록 (소유자 · 운영자)").
 *
 * @param members 지금 운영진. 나간 사람은 빠진다.
 */
public record StaffListResult(List<Member> members) {

    public StaffListResult {
        members = List.copyOf(members);
    }

    /**
     * 운영진 한 사람 — 실명 · 이메일 대신 닉네임으로 보인다.
     *
     * @param staffAccountId 운영진 계정 id — 내보낼 사람을 가리킬 때 쓴다
     * @param nickname       닉네임
     * @param role           그 주최자에서의 역할
     * @param since          지금 역할로 소속된 일시
     */
    public record Member(long staffAccountId, String nickname, StaffRole role, Instant since) {

        public Member {
            Objects.requireNonNull(nickname, "nickname");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(since, "since");
        }
    }
}
