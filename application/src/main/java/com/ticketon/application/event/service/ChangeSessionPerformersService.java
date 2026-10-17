package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.ChangeSessionPerformersCommand;
import com.ticketon.application.event.port.in.ChangeSessionPerformersUseCase;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.application.event.service.EventScheduleEditing.LockedSchedule;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventChangeHistory;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.SessionPerformers;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 출연 — 바꾸는 규칙(수정 범위, 같은 값이면 바꾸지 않음, 진행 단계 · 마지막 수정 시각)은 {@link Event#changeSessionPerformers}가 갖고,
 * 여기서는 줄을 잠가 읽은 뒤 바뀌었으면 일정 · 행사를 저장하고 조작 기록을 남긴다({@link EventScheduleEditing}).
 * <p>
 * <b>변경 이력</b> — 판매를 시작한 뒤 바뀌면 바꾸기 전 출연과 견줘 그 회차의 새 버전을 쌓는다({@link EventChangeHistory#performersVersion},
 * {@link EventChangeHistoryRecorder}). 일정 · 행사를 저장한 같은 트랜잭션이고, 행사 줄을 쥔 채 쌓는다. 작성 중에는 쌓지 않는다. 출연 칸을 고치는
 * 것만으로는 출연자 변경 알림이 되지 않는다(docs/02).
 */
@Service
public class ChangeSessionPerformersService implements ChangeSessionPerformersUseCase {

    private final EventScheduleEditing editing;
    private final EventChangeHistoryRecorder historyRecorder;

    public ChangeSessionPerformersService(EventScheduleEditing editing, EventChangeHistoryRecorder historyRecorder) {
        this.editing = editing;
        this.historyRecorder = historyRecorder;
    }

    /** 조작 기록의 요약 — "출연 변경 · 2026-03-14 19:00 (2단계)". 출연 칸의 내용은 일정에 있다. */
    static String operationSummary(String sessionLabel) {
        return "출연 변경 · " + sessionLabel + " (2단계)";
    }

    @Override
    @Transactional
    public EventScheduleResult change(ChangeSessionPerformersCommand command) {
        LockedSchedule locked = editing.lock(command.actingStaff(), command.eventId());
        Event event = locked.event();
        EventSchedule schedule = locked.requireSchedule();
        Set<Long> sessionIdsBefore = ScheduleResults.sessionIdsOf(schedule);
        Optional<SessionPerformers> before = schedule.session(command.sessionId()).performers();

        if (!event.changeSessionPerformers(schedule, command.sessionId(), command.performers(), locked.now())) {
            return ScheduleResults.of(schedule);
        }
        EventScheduleResult saved = editing.saveChanged(locked, schedule, sessionIdsBefore,
                operationSummary(ScheduleResults.sessionLabel(schedule, command.sessionId())));
        historyRecorder.record(locked.permission(), command.eventId(),
                EventChangeHistory.performersVersion(event, schedule, command.sessionId(), before).stream().toList(), locked.now());
        return saved;
    }
}
