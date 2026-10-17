package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.ChooseSessionModeCommand;
import com.ticketon.application.event.port.in.ChooseSessionModeUseCase;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.application.event.service.EventScheduleEditing.LockedSchedule;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.EventSchedule;
import com.ticketon.domain.event.SessionMode;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 방식 선택(위저드 2단계) — 처음 고르면 일정이 생기고({@link Event#startSchedule}), 다시 보내면 회차 방식 · 기본 운영 시간을 고친다
 * ({@link Event#changeSessionMode}). 규칙은 행사가 갖고, 여기서는 줄을 잠가 읽은 뒤 바뀌었으면 일정 · 행사를 저장하고 조작 기록을 남긴다
 * ({@link EventScheduleEditing}). 같은 값이면 저장도 기록도 하지 않는다.
 */
@Service
public class ChooseSessionModeService implements ChooseSessionModeUseCase {

    private final EventScheduleEditing editing;

    public ChooseSessionModeService(EventScheduleEditing editing) {
        this.editing = editing;
    }

    /** 조작 기록의 요약 — 저장한 뒤의 값으로 "회차 방식 · 회차형 (2단계)", "회차 방식 · 종일형 · 기본 운영 시간 10:00~18:00 (2단계)". */
    static String operationSummary(EventSchedule schedule) {
        if (schedule.mode() == SessionMode.TIMED) {
            return "회차 방식 · 회차형 (2단계)";
        }
        return "회차 방식 · 종일형 · 기본 운영 시간 " + ScheduleResults.hoursLabel(schedule.defaultOperatingHours().orElseThrow()) + " (2단계)";
    }

    @Override
    @Transactional
    public EventScheduleResult choose(ChooseSessionModeCommand command) {
        LockedSchedule locked = editing.lock(command.actingStaff(), command.eventId());
        Event event = locked.event();
        if (locked.schedule().isEmpty()) {
            EventSchedule started = event.startSchedule(command.sessionMode(), command.defaultOperatingHours(), locked.now());
            return editing.saveChanged(locked, started, Set.of(), operationSummary(started));
        }
        EventSchedule schedule = locked.schedule().get();
        Set<Long> sessionIdsBefore = ScheduleResults.sessionIdsOf(schedule);
        if (!event.changeSessionMode(schedule, command.sessionMode(), command.defaultOperatingHours(), locked.now())) {
            return ScheduleResults.of(schedule);
        }
        return editing.saveChanged(locked, schedule, sessionIdsBefore, operationSummary(schedule));
    }
}
