package com.ticketon.domain.event;

import com.ticketon.domain.error.NotFoundException;
import com.ticketon.domain.error.RuleViolationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 행사의 입구 목록 — "정문", "2층 전용", "VIP 입구"(docs/02 "입구 (게이트)"). <b>도면에 속한다</b> — 회차마다 달라지지 않는다. 위저드 6단계에서
 * 넣고, 판매 시작 뒤에도 입력할 수 있다(docs/03 "행사 만들기 위저드").
 * <p>
 * 도면({@link FloorPlan})과 따로 두는 까닭은 고칠 수 있는 기간이 달라서다. 판매가 끝나 도면 · 가격 · 회차가 고정된 뒤에도 입구 목록은 행사가 끝날
 * 때까지 고친다 — 18:10에 정문 줄이 길어 옆문을 더 열면 입구 목록에 "옆문"을 추가해야 스태프가 고를 수 있다(docs/04 "판매 종료는 공연이 끝난 때가
 * 아닙니다"). 구역 · 블록과 이름을 맞춰 볼 규칙도 없다. 언제 고칠 수 있는지는 행사 상태가 정한다({@link EventEditTarget#ENTRANCES},
 * {@link Event#requireEditable}).
 * <p>
 * 확인 필요 (#37) — 같은 이름의 입구 둘은 받지 않는다({@link EventErrorCode#ENTRANCE_NAME_ALREADY_TAKEN}). 검표원이 고르는 목록과 게이트별 입장
 * 현황이 이름으로 입구를 보여 주므로, 같은 이름 둘은 가를 수 없다. 이름은 앞뒤 공백을 걷고 NFC로 맞춘 값끼리 견준다.
 * <p>
 * 입구를 지워도 그 입구를 가리키는 입장 기록을 남기는 일은 저장소가 한다(#42).
 */
public final class EventEntrances {

    private final long eventId;
    private final List<Entrance> entrances;

    private EventEntrances(long eventId, List<Entrance> entrances) {
        if (eventId < 1) {
            throw new IllegalArgumentException("행사 id는 1 이상이어야 합니다: " + eventId);
        }
        this.eventId = eventId;
        this.entrances = new ArrayList<>(entrances);
        requireConsistent();
    }

    /** 입구가 아직 없는 목록. */
    public static EventEntrances empty(long eventId) {
        return new EventEntrances(eventId, List.of());
    }

    /**
     * 저장소에서 읽은 값으로 입구 목록을 되살린다. 입구는 모두 저장된 것(id가 있는 것)이어야 한다.
     *
     * @throws IllegalArgumentException id가 없거나, 이름이나 id가 겹치면
     */
    public static EventEntrances restore(long eventId, List<Entrance> entrances) {
        Objects.requireNonNull(entrances, "entrances");
        entrances.forEach(entrance -> {
            if (entrance.id().isEmpty()) {
                throw new IllegalArgumentException("저장소에서 읽은 입구 목록의 입구에 id가 없습니다");
            }
        });
        return new EventEntrances(eventId, entrances);
    }

    /**
     * 입구를 더한다.
     *
     * @return 더한 입구(아직 id 없음)
     * @throws RuleViolationException 같은 이름의 입구가 있으면({@link EventErrorCode#ENTRANCE_NAME_ALREADY_TAKEN})
     */
    public Entrance add(EntranceName name) {
        requireNameAvailable(name, null);
        Entrance entrance = Entrance.newEntrance(name);
        entrances.add(entrance);
        return entrance;
    }

    /**
     * 입구 이름을 바꾼다.
     *
     * @return 바뀌었는지 — 지금과 같으면 false
     * @throws NotFoundException      목록에 없는 입구면({@link EventErrorCode#ENTRANCE_NOT_FOUND})
     * @throws RuleViolationException 다른 입구가 쓰는 이름이면({@link EventErrorCode#ENTRANCE_NAME_ALREADY_TAKEN})
     */
    public boolean rename(long entranceId, EntranceName newName) {
        Entrance entrance = entrance(entranceId);
        requireNameAvailable(newName, entrance);
        return entrance.rename(newName);
    }

    /**
     * 입구를 지운다.
     *
     * @throws NotFoundException 목록에 없는 입구면({@link EventErrorCode#ENTRANCE_NOT_FOUND})
     */
    public void remove(long entranceId) {
        entrances.remove(entrance(entranceId));
    }

    /**
     * 그 입구.
     *
     * @throws NotFoundException 목록에 없는 입구면({@link EventErrorCode#ENTRANCE_NOT_FOUND})
     */
    public Entrance entrance(long entranceId) {
        return entrances.stream()
                .filter(entrance -> entrance.hasId(entranceId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(EventErrorCode.ENTRANCE_NOT_FOUND, "행사 " + eventId + ", 입구 " + entranceId));
    }

    public long eventId() {
        return eventId;
    }

    /** 입구 — 더한 순서. */
    public List<Entrance> entrances() {
        return Collections.unmodifiableList(entrances);
    }

    private void requireNameAvailable(EntranceName name, Entrance excluded) {
        Objects.requireNonNull(name, "name");
        boolean taken = entrances.stream()
                .filter(entrance -> entrance != excluded)
                .anyMatch(entrance -> entrance.name().equals(name));
        if (taken) {
            throw new RuleViolationException(EventErrorCode.ENTRANCE_NAME_ALREADY_TAKEN, "행사 " + eventId + ", 입구 이름 " + name.value());
        }
    }

    private void requireConsistent() {
        Set<EntranceName> seenNames = new HashSet<>();
        Set<Long> seenIds = new HashSet<>();
        for (Entrance entrance : entrances) {
            Objects.requireNonNull(entrance, "entrance");
            if (!seenNames.add(entrance.name())) {
                throw new IllegalArgumentException("입구 이름이 겹칩니다: 행사 " + eventId + ", " + entrance.name().value());
            }
            entrance.id().ifPresent(entranceId -> {
                if (!seenIds.add(entranceId)) {
                    throw new IllegalArgumentException("입구 id가 겹칩니다: " + entranceId);
                }
            });
        }
    }
}
