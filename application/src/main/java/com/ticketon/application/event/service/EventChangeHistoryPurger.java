package com.ticketon.application.event.service;

import com.ticketon.application.event.port.out.EventChangeHistoryRepository;
import com.ticketon.application.event.port.out.EventPosterStoragePort;
import com.ticketon.application.event.port.out.EventScheduleRepository;
import com.ticketon.domain.event.EventChangeHistory;
import com.ticketon.domain.event.EventChangeHistoryVersion;
import com.ticketon.domain.event.EventSchedule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 하나의 변경 이력을 보관 기간이 지났으면 지운다 — 행사 하나가 트랜잭션 하나다({@link PurgeExpiredEventChangeHistoryService}).
 * <ol>
 *   <li>행사 줄을 FOR KEY SHARE로 잡는다({@link EventChangeHistoryRepository#lockEventForPurge}) — 같은 행사의 편집(행사 줄 FOR UPDATE)이 진행
 *       중이면 끝날 때까지 기다린다. 잡은 동안에는 일정도 이력도 다른 흐름이 고치지 못한다.</li>
 *   <li>일정을 읽어 행사가 끝난 날 + 6개월이 지났는지 본다({@link EventChangeHistory#isPurgeDue}) — 기다리는 사이 회차가 늘어 끝난 날이 늦어졌으면
 *       지우지 않는다.</li>
 *   <li>그 행사의 이력을 모두 지운다 — 잡기 전에 쌓인 버전까지 지운다.</li>
 *   <li>이력에만 남아 있던 바꾸기 전 포스터 파일을 커밋된 뒤 지운다({@link EventChangeHistory#replacedPosters}, {@link PosterFileCleanup}) — 먼저
 *       지웠다가 삭제가 되돌려지면 이력이 없는 파일을 가리킨다. 지금 포스터 파일은 행사와 함께 남는다.</li>
 * </ol>
 * 잠금 순서: 행사 줄(③)만 FOR KEY SHARE로 잡는다 — 이력 줄을 지우는 일은 다른 줄에 잠금을 걸지 않고, 일정은 잠그지 않고 읽는다(organizer
 * 서비스의 {@code StaffAccountLocks} 표). 운영진 계정 줄 · 주최자 줄을 기다리지 않는다.
 */
@Component
class EventChangeHistoryPurger {

    private final EventChangeHistoryRepository historyRepository;
    private final EventScheduleRepository scheduleRepository;
    private final EventPosterStoragePort posterStorage;

    EventChangeHistoryPurger(EventChangeHistoryRepository historyRepository, EventScheduleRepository scheduleRepository,
            EventPosterStoragePort posterStorage) {
        this.historyRepository = historyRepository;
        this.scheduleRepository = scheduleRepository;
        this.posterStorage = posterStorage;
    }

    /**
     * @param now 이번 작업의 지금 — 보관 기간 판정에 쓴다
     * @return 이번에 지웠는지
     */
    @Transactional
    public boolean purgeIfExpired(long eventId, Instant now) {
        if (!historyRepository.lockEventForPurge(eventId)) {
            return false;
        }
        Optional<EventSchedule> schedule = scheduleRepository.findByEventId(eventId);
        if (schedule.isEmpty() || !EventChangeHistory.isPurgeDue(schedule.get(), now)) {
            return false;
        }
        List<EventChangeHistoryVersion> deleted = historyRepository.deleteByEventId(eventId);
        EventChangeHistory.replacedPosters(deleted)
                .forEach(replacedPoster -> PosterFileCleanup.deleteAfterCommit(posterStorage, replacedPoster));
        return !deleted.isEmpty();
    }
}
