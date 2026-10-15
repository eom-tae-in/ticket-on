package com.ticketon.domain.organizer;

import java.time.Instant;
import java.util.Objects;

/**
 * 소속 한 건 — 어느 사람(운영진 로그인)이 어느 주최자에 어떤 역할로 언제부터 언제까지 있었는지(docs/14 "무엇이 무엇을 가리키나":
 * 소속 = 운영진(사람) + 주최자 + 역할 + 기간).
 * <p>
 * <b>나가도 지우지 않고 이력으로 남는다.</b> 운영진 프로필의 "참여한 주최자"(이름 · 역할 · 기간)와 관리자 판단에 쓰인다
 * (docs/01 "운영진 프로필", docs/11 "데이터 보관 — 운영진 소속 이력"). 역할이 바뀌면(소유자 넘기기) 그때까지의 소속을 끝내고 새 역할의
 * 소속을 같은 시각에 시작한다 — 한 건의 역할은 바뀌지 않아 "운영자 · 2019~2023, 소유자 · 2023~"처럼 기간마다 역할이 남는다.
 * 사람은 운영진 계정 id로 가리킨다 — 닉네임을 바꿔도 이력은 그대로 따라간다.
 *
 * @param organizerId    주최자 id
 * @param staffAccountId 소속된 사람의 운영진 계정 id
 * @param role           역할
 * @param startedAt      소속 시작 일시
 * @param endedAt        소속 종료 일시. 지금 소속돼 있으면 null. 시작보다 앞설 수 없다.
 */
public record StaffMembership(long organizerId, long staffAccountId, StaffRole role, Instant startedAt, Instant endedAt) {

    public StaffMembership {
        if (organizerId < 1) {
            throw new IllegalArgumentException("주최자 id는 1 이상이어야 합니다: " + organizerId);
        }
        if (staffAccountId < 1) {
            throw new IllegalArgumentException("운영진 계정 id는 1 이상이어야 합니다: " + staffAccountId);
        }
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(startedAt, "startedAt");
        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("소속 종료(" + endedAt + ")가 시작(" + startedAt + ")보다 앞설 수 없습니다");
        }
    }

    /** 지금 시작하는 소속. */
    static StaffMembership startingAt(long organizerId, long staffAccountId, StaffRole role, Instant now) {
        return new StaffMembership(organizerId, staffAccountId, role, now, null);
    }

    /** 지금 소속돼 있는지 — 아직 끝나지 않았다. */
    public boolean isActive() {
        return endedAt == null;
    }

    /** 이 소속을 지금 끝낸 것. 사람·주최자·역할·시작은 그대로 남는다. */
    StaffMembership endAt(Instant now) {
        Objects.requireNonNull(now, "now");
        if (!isActive()) {
            throw new IllegalStateException("이미 끝난 소속입니다");
        }
        return new StaffMembership(organizerId, staffAccountId, role, startedAt, now);
    }
}
