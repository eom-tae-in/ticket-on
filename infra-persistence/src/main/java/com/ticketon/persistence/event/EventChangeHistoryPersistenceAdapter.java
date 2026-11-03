package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.EVENT;
import static com.ticketon.persistence.jooq.Tables.EVENT_CHANGE_HISTORY;

import com.ticketon.application.event.port.out.EventChangeHistoryRepository;
import com.ticketon.domain.event.EventChangeAuthor;
import com.ticketon.domain.event.EventChangeHistoryTarget;
import com.ticketon.domain.event.EventChangeHistoryVersion;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 행사 변경 이력 저장소 어댑터 — 변경 이력 표(event_change_history — V26)에 버전을 쌓고, 행사마다 지운다.
 * <ul>
 *   <li>쌓기: 버전마다 그 칸(행사 · 대상 칸 · 회차)의 지금 버전(유효 끝이 없는 줄)의 유효 끝을 그 순간으로 고친 뒤, 그 순간부터 유효한 새 줄을
 *       넣는다. 지금 버전이 하나뿐인 것은 유일 색인 두 개가 막는다. 부르는 흐름이 행사 줄을 FOR UPDATE로 쥐고 있어 같은 행사의 쌓기는 차례로 돈다.</li>
 *   <li>지우기 전 잠금: 행사 줄만 FOR KEY SHARE로 잡는다 — 지운 행사의 줄도 잡는다(이력이 지운 행사를 가리킬 수 있다).</li>
 *   <li>지우기: DELETE … RETURNING — 지운 줄의 칸을 돌려준다.</li>
 * </ul>
 */
@Component
public class EventChangeHistoryPersistenceAdapter implements EventChangeHistoryRepository {

    private final DSLContext dsl;

    public EventChangeHistoryPersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public boolean existsByEventId(long eventId) {
        return dsl.fetchExists(DSL.selectOne().from(EVENT_CHANGE_HISTORY).where(EVENT_CHANGE_HISTORY.EVENT_ID.eq(eventId)));
    }

    @Override
    public void add(long eventId, List<EventChangeHistoryVersion> versions, EventChangeAuthor author, Instant changedAt) {
        Objects.requireNonNull(versions, "versions");
        Objects.requireNonNull(author, "author");
        requireOnePerItem(eventId, versions);
        OffsetDateTime at = toDatabaseTime(changedAt);
        for (EventChangeHistoryVersion version : versions) {
            dsl.update(EVENT_CHANGE_HISTORY)
                    .set(EVENT_CHANGE_HISTORY.VALID_UNTIL, at)
                    .where(isSameItem(eventId, version))
                    .and(EVENT_CHANGE_HISTORY.VALID_UNTIL.isNull())
                    .execute();
            dsl.insertInto(EVENT_CHANGE_HISTORY)
                    .set(EVENT_CHANGE_HISTORY.EVENT_ID, eventId)
                    .set(EVENT_CHANGE_HISTORY.TARGET, version.target().name())
                    .set(EVENT_CHANGE_HISTORY.SESSION_ID, version.sessionId())
                    .set(EVENT_CHANGE_HISTORY.PREVIOUS_CONTENT, version.previousContent())
                    .set(EVENT_CHANGE_HISTORY.NEW_CONTENT, version.newContent())
                    .set(EVENT_CHANGE_HISTORY.VALID_FROM, at)
                    .set(EVENT_CHANGE_HISTORY.CHANGED_BY_STAFF_ACCOUNT_ID, author.staffAccountId())
                    .set(EVENT_CHANGE_HISTORY.CHANGED_BY_NICKNAME, author.nickname().value())
                    .set(EVENT_CHANGE_HISTORY.NOTICE_SENT, false)
                    .execute();
        }
    }

    @Override
    public List<Long> findEventIdsWithHistory() {
        return dsl.selectDistinct(EVENT_CHANGE_HISTORY.EVENT_ID)
                .from(EVENT_CHANGE_HISTORY)
                .orderBy(EVENT_CHANGE_HISTORY.EVENT_ID)
                .fetch(EVENT_CHANGE_HISTORY.EVENT_ID);
    }

    @Override
    public boolean lockEventForPurge(long eventId) {
        return dsl.select(EVENT.ID)
                .from(EVENT)
                .where(EVENT.ID.eq(eventId))
                .forKeyShare()
                .fetchOptional()
                .isPresent();
    }

    @Override
    public List<EventChangeHistoryVersion> deleteByEventId(long eventId) {
        return dsl.deleteFrom(EVENT_CHANGE_HISTORY)
                .where(EVENT_CHANGE_HISTORY.EVENT_ID.eq(eventId))
                .returning(EVENT_CHANGE_HISTORY.ID, EVENT_CHANGE_HISTORY.TARGET, EVENT_CHANGE_HISTORY.SESSION_ID,
                        EVENT_CHANGE_HISTORY.PREVIOUS_CONTENT, EVENT_CHANGE_HISTORY.NEW_CONTENT)
                .fetch()
                .sortAsc(EVENT_CHANGE_HISTORY.ID)
                .map(row -> new EventChangeHistoryVersion(EventChangeHistoryTarget.valueOf(row.getTarget()), row.getSessionId(),
                        row.getPreviousContent(), row.getNewContent()));
    }

    /** 같은 칸 — 행사 · 대상 칸이 같고, 회차 출연이면 회차도 같다. */
    private static Condition isSameItem(long eventId, EventChangeHistoryVersion version) {
        Condition sameTarget = EVENT_CHANGE_HISTORY.EVENT_ID.eq(eventId)
                .and(EVENT_CHANGE_HISTORY.TARGET.eq(version.target().name()));
        return version.sessionId() == null
                ? sameTarget.and(EVENT_CHANGE_HISTORY.SESSION_ID.isNull())
                : sameTarget.and(EVENT_CHANGE_HISTORY.SESSION_ID.eq(version.sessionId()));
    }

    /** 한 번에 같은 칸을 두 번 쌓지 않는다 — 뒤의 버전이 앞의 버전을 같은 순간에 닫아 길이가 0인 버전이 생긴다. */
    private static void requireOnePerItem(long eventId, List<EventChangeHistoryVersion> versions) {
        Set<String> items = new HashSet<>();
        for (EventChangeHistoryVersion version : versions) {
            if (!items.add(version.target() + ":" + version.sessionId())) {
                throw new IllegalArgumentException("같은 칸의 버전이 둘 이상입니다: 행사 " + eventId + ", " + version.target()
                        + (version.sessionId() == null ? "" : ", 회차 " + version.sessionId()));
            }
        }
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
