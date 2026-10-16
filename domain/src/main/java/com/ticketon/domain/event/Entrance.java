package com.ticketon.domain.event;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * 입구(게이트) — 주최자가 미리 등록하는 입장 지점(docs/02 "입구 (게이트)"). 검표 시작 시 고를 대상이 되고, 입장 기록이 입구를 가리켜 게이트별
 * 입장 현황이 통계로 나온다(docs/14 "입장 기록 — 티켓 + 회차 + 입구", docs/11).
 * <p>
 * 이름만 갖는다. 바꾸는 일은 행사의 입구 목록이 한다({@link EventEntrances}). 저장소에서 다시 읽을 때는 {@link #restore}를 쓴다. 저장 전 입구는
 * id가 없다.
 */
public final class Entrance {

    private final Long id;
    private EntranceName name;

    private Entrance(Long id, EntranceName name) {
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
    }

    static Entrance newEntrance(EntranceName name) {
        return new Entrance(null, name);
    }

    /** 저장소에서 읽은 값으로 입구를 되살린다. */
    public static Entrance restore(long id, EntranceName name) {
        if (id < 1) {
            throw new IllegalArgumentException("입구 id는 1 이상이어야 합니다: " + id);
        }
        return new Entrance(id, name);
    }

    /** 저장 전이면 비어 있다. */
    public OptionalLong id() {
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    public EntranceName name() {
        return name;
    }

    boolean hasId(long entranceId) {
        return id != null && id == entranceId;
    }

    /** @return 바뀌었는지 */
    boolean rename(EntranceName newName) {
        Objects.requireNonNull(newName, "newName");
        if (newName.equals(name)) {
            return false;
        }
        name = newName;
        return true;
    }
}
