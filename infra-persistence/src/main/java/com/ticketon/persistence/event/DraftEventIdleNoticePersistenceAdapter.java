package com.ticketon.persistence.event;

import static com.ticketon.persistence.jooq.Tables.DRAFT_EVENT_IDLE_NOTICE;
import static com.ticketon.persistence.jooq.Tables.EVENT;
import static com.ticketon.persistence.jooq.Tables.ORGANIZER;

import com.ticketon.application.event.port.out.DraftEventIdleNoticeRepository;
import com.ticketon.application.event.query.IdleDraftEvent;
import com.ticketon.domain.event.EventStatus;
import com.ticketon.domain.event.EventWizardStep;
import com.ticketon.domain.organizer.OrganizerStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * 작성 중 행사 30일 미수정 알림 저장소 어댑터 — 행사 표(event — V19)와 주최자 표(organizer — V8)에서 대상 행사를 찾고, 알림 기록 표
 * (draft_event_idle_notice — V25)에 (행사, 마지막 수정 시각)마다 한 줄을 넣는다.
 * <ul>
 *   <li>대상: 활동 중인 주최자의 지우지 않은 작성 중 행사.</li>
 *   <li>찾기: 대상 가운데 마지막 수정이 상한 이전이고, 지금 마지막 수정 시각의 기록이 없는 것 — 잠그지 않고 읽는다.</li>
 *   <li>다시 읽기: 행사 줄만 FOR KEY SHARE로 잡는다(주최자 줄은 잡지 않는다). 위저드 저장(FOR UPDATE)을 기다렸다가 바뀐 줄로 조건을 다시 본다.</li>
 *   <li>남기기: INSERT … ON CONFLICT DO NOTHING — 기본 키가 같은 구간의 두 번째 기록을 받지 않는다. 다른 트랜잭션이 같은 키를 막 넣는 중이면
 *       PostgreSQL이 그 트랜잭션이 끝날 때까지 기다린 뒤 판단한다.</li>
 * </ul>
 */
@Component
public class DraftEventIdleNoticePersistenceAdapter implements DraftEventIdleNoticeRepository {

    private final DSLContext dsl;

    public DraftEventIdleNoticePersistenceAdapter(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<Long> findNoticeCandidates(Instant idleCutoff) {
        return dsl.select(EVENT.ID)
                .from(EVENT)
                .join(ORGANIZER).on(ORGANIZER.ID.eq(EVENT.ORGANIZER_ID))
                .where(isNoticeTarget())
                .and(EVENT.LAST_MODIFIED_AT.le(toDatabaseTime(idleCutoff)))
                .andNotExists(DSL.selectOne()
                        .from(DRAFT_EVENT_IDLE_NOTICE)
                        .where(DRAFT_EVENT_IDLE_NOTICE.EVENT_ID.eq(EVENT.ID))
                        .and(DRAFT_EVENT_IDLE_NOTICE.LAST_MODIFIED_AT.eq(EVENT.LAST_MODIFIED_AT)))
                .orderBy(EVENT.ID)
                .fetch(EVENT.ID);
    }

    @Override
    public Optional<IdleDraftEvent> lockDraftEvent(long eventId) {
        return dsl.select(EVENT.ID, EVENT.ORGANIZER_ID, ORGANIZER.NAME, EVENT.TITLE, EVENT.WIZARD_PROGRESS_STEP, EVENT.LAST_MODIFIED_AT)
                .from(EVENT)
                .join(ORGANIZER).on(ORGANIZER.ID.eq(EVENT.ORGANIZER_ID))
                .where(EVENT.ID.eq(eventId))
                .and(isNoticeTarget())
                .forKeyShare().of(EVENT)
                .fetchOptional(row -> new IdleDraftEvent(row.value1(), row.value2(), row.value3(), row.value4(),
                        EventWizardStep.valueOf(row.value5()), row.value6().toInstant()));
    }

    @Override
    public boolean recordNotice(long eventId, Instant lastModifiedAt, Instant notifiedAt) {
        return dsl.insertInto(DRAFT_EVENT_IDLE_NOTICE)
                .set(DRAFT_EVENT_IDLE_NOTICE.EVENT_ID, eventId)
                .set(DRAFT_EVENT_IDLE_NOTICE.LAST_MODIFIED_AT, toDatabaseTime(lastModifiedAt))
                .set(DRAFT_EVENT_IDLE_NOTICE.NOTIFIED_AT, toDatabaseTime(notifiedAt))
                .onConflictDoNothing()
                .execute() == 1;
    }

    /** 활동 중인 주최자의 지우지 않은 작성 중 행사. */
    private static Condition isNoticeTarget() {
        return EVENT.STATUS.eq(EventStatus.DRAFT.name())
                .and(EVENT.DELETED_AT.isNull())
                .and(ORGANIZER.STATUS.eq(OrganizerStatus.ACTIVE.name()));
    }

    private static OffsetDateTime toDatabaseTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
