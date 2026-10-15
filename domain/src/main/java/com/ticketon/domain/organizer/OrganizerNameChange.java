package com.ticketon.domain.organizer;

import java.time.Instant;
import java.util.Objects;

/**
 * 주최자명 변경 이력 한 건 — 언제, 무엇에서 무엇으로, 누가 바꿨는지(docs/01 "공개 페이지 — 주최자명 변경").
 * <p>
 * 주최자명은 소유자가 바로 바꾸고 이력을 남긴다. 행사는 주최자를 가리키기만 하므로 이름을 바꾸면 지난 행사 페이지에도 새 이름이 뜬다.
 * 행사 페이지는 표시·광고라 "샀을 때 무슨 이름이었나"를 확인할 근거로 이 이력을 남긴다(docs/01). 이력은 주최자를 가리킨다
 * (docs/14 "무엇이 무엇을 가리키나"). 저장된 이력 한 줄과 관리자의 사후 확인은 {@link OrganizerNameChangeEntry}가 갖는다.
 *
 * @param previousName           바꾸기 전 이름
 * @param newName                바꾼 이름. 바꾸기 전과 달라야 한다.
 * @param changedByStaffAccountId 바꾼 사람의 운영진 계정 id
 * @param changedAt              바꾼 일시
 */
public record OrganizerNameChange(OrganizerName previousName, OrganizerName newName, long changedByStaffAccountId,
        Instant changedAt) {

    public OrganizerNameChange {
        Objects.requireNonNull(previousName, "previousName");
        Objects.requireNonNull(newName, "newName");
        if (previousName.equals(newName)) {
            throw new IllegalArgumentException("바꾸기 전과 같은 이름입니다: " + newName.value());
        }
        if (changedByStaffAccountId < 1) {
            throw new IllegalArgumentException("운영진 계정 id는 1 이상이어야 합니다: " + changedByStaffAccountId);
        }
        Objects.requireNonNull(changedAt, "changedAt");
    }
}
