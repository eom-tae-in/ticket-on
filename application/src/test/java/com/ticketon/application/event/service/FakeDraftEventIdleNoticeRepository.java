package com.ticketon.application.event.service;

import com.ticketon.application.event.port.out.DraftEventIdleNoticeRepository;
import com.ticketon.application.event.query.IdleDraftEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.LongConsumer;

/**
 * 메모리 알림 저장소 — 대상 행사(활동 중인 주최자의 지우지 않은 작성 중 행사)만 담고, 알린 구간을 (행사, 마지막 수정 시각)으로 기억한다. 행사를
 * 고치거나 대상에서 빼는 것은 테스트가 한다. 찾은 뒤 다시 읽기 전에 끼어드는 일(그사이 고치기 등)은 {@link #beforeLock}로 흉내 낸다.
 */
final class FakeDraftEventIdleNoticeRepository implements DraftEventIdleNoticeRepository {

    private final Map<Long, IdleDraftEvent> drafts = new TreeMap<>();
    private final Set<String> notices = new HashSet<>();
    private final List<Long> lockedEventIds = new ArrayList<>();
    private LongConsumer beforeLock = eventId -> {
    };

    void store(IdleDraftEvent draft) {
        drafts.put(draft.eventId(), draft);
    }

    /** 고친다 — 마지막 수정 시각이 바뀐다. */
    void edit(long eventId, Instant editedAt) {
        IdleDraftEvent draft = drafts.get(eventId);
        drafts.put(eventId, new IdleDraftEvent(eventId, draft.organizerId(), draft.organizerName(), draft.title(),
                draft.wizardProgress(), editedAt));
    }

    /** 대상에서 뺀다 — 판매를 시작했거나 지웠거나 주최자가 닫혔다. */
    void remove(long eventId) {
        drafts.remove(eventId);
    }

    /** 다시 읽기 직전에 할 일. */
    void beforeLock(LongConsumer action) {
        beforeLock = action;
    }

    List<Long> lockedEventIds() {
        return List.copyOf(lockedEventIds);
    }

    int noticeCount() {
        return notices.size();
    }

    @Override
    public List<Long> findNoticeCandidates(Instant idleCutoff) {
        return drafts.values().stream()
                .filter(draft -> !draft.lastModifiedAt().isAfter(idleCutoff))
                .filter(draft -> !notices.contains(keyOf(draft.eventId(), draft.lastModifiedAt())))
                .sorted(Comparator.comparingLong(IdleDraftEvent::eventId))
                .map(IdleDraftEvent::eventId)
                .toList();
    }

    @Override
    public Optional<IdleDraftEvent> lockDraftEvent(long eventId) {
        beforeLock.accept(eventId);
        lockedEventIds.add(eventId);
        return Optional.ofNullable(drafts.get(eventId));
    }

    @Override
    public boolean recordNotice(long eventId, Instant lastModifiedAt, Instant notifiedAt) {
        if (!notifiedAt.isAfter(lastModifiedAt)) {
            throw new IllegalArgumentException("알린 시각은 구간이 시작된 뒤여야 합니다");
        }
        return notices.add(keyOf(eventId, lastModifiedAt));
    }

    private static String keyOf(long eventId, Instant lastModifiedAt) {
        return eventId + "@" + lastModifiedAt;
    }
}
