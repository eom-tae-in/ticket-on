package com.ticketon.application.event.service;

import com.ticketon.application.event.port.out.EventChangeHistoryRepository;
import com.ticketon.domain.event.EventChangeAuthor;
import com.ticketon.domain.event.EventChangeHistoryVersion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 메모리 변경 이력 저장소 — 표처럼 버전 한 줄마다 유효 기간 · 바꾼 사람을 갖고, 쌓을 때 같은 칸(회차 출연은 회차마다)의 지금 버전을 그 순간으로
 * 닫는다(진짜 저장소가 UPDATE 뒤 INSERT하듯이). 지우기 전 잠금은 잠금 기록에 차례만 남긴다. 쌓기를 실패하게 둘 수 있다.
 */
final class FakeEventChangeHistoryRepository implements EventChangeHistoryRepository {

    /** 저장된 버전 한 줄. */
    record StoredVersion(long id, long eventId, EventChangeHistoryVersion version, Instant validFrom, Instant validUntil,
            EventChangeAuthor author, boolean noticeSent) {

        boolean isSameItem(long otherEventId, EventChangeHistoryVersion other) {
            return eventId == otherEventId && version.target() == other.target()
                    && Objects.equals(version.sessionId(), other.sessionId());
        }

        StoredVersion closedAt(Instant until) {
            return new StoredVersion(id, eventId, version, validFrom, until, author, noticeSent);
        }
    }

    private final List<StoredVersion> rows = new ArrayList<>();
    private final RowLockLog rowLockLog;
    private long nextId = 1;
    private boolean failingAdds;

    FakeEventChangeHistoryRepository(RowLockLog rowLockLog) {
        this.rowLockLog = rowLockLog;
    }

    /** 그 행사의 버전 — 쌓은 차례대로. */
    List<StoredVersion> versionsOf(long eventId) {
        return rows.stream().filter(row -> row.eventId() == eventId).toList();
    }

    /** 이후의 쌓기를 실패하게 한다 — 트랜잭션이 되돌려지는 것을 흉내 낸다. */
    void failAdds() {
        failingAdds = true;
    }

    @Override
    public boolean existsByEventId(long eventId) {
        return rows.stream().anyMatch(row -> row.eventId() == eventId);
    }

    @Override
    public void add(long eventId, List<EventChangeHistoryVersion> versions, EventChangeAuthor author, Instant changedAt) {
        Set<String> items = new HashSet<>();
        versions.forEach(version -> {
            if (!items.add(version.target() + ":" + version.sessionId())) {
                throw new IllegalArgumentException("같은 칸의 버전이 둘 이상입니다: " + version.target());
            }
        });
        if (failingAdds) {
            throw new IllegalStateException("이력을 쌓지 못했습니다(테스트가 실패하게 함): 행사 " + eventId);
        }
        for (EventChangeHistoryVersion version : versions) {
            rows.replaceAll(row -> row.isSameItem(eventId, version) && row.validUntil() == null ? row.closedAt(changedAt) : row);
            rows.add(new StoredVersion(nextId++, eventId, version, changedAt, null, author, false));
        }
    }

    @Override
    public List<Long> findEventIdsWithHistory() {
        return rows.stream().map(StoredVersion::eventId).distinct().sorted().toList();
    }

    @Override
    public boolean lockEventForPurge(long eventId) {
        rowLockLog.record("행사 " + eventId + " FOR KEY SHARE");
        return true;
    }

    @Override
    public List<EventChangeHistoryVersion> deleteByEventId(long eventId) {
        List<EventChangeHistoryVersion> deleted = versionsOf(eventId).stream().map(StoredVersion::version).toList();
        rows.removeIf(row -> row.eventId() == eventId);
        return deleted;
    }
}
