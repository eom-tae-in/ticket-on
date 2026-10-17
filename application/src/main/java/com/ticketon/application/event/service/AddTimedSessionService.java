package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.AddTimedSessionCommand;
import com.ticketon.application.event.port.in.AddTimedSessionUseCase;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.SessionAdded;
import com.ticketon.domain.event.TimedSessionTime;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차형 회차 추가(위저드 2단계) — 더하는 규칙(수정 범위, 회차 방식, 같은 날짜의 시작 시각 겹침, 진행 단계 · 마지막 수정 시각)은
 * {@link Event#addTimedSession}이 갖고, 여기서는 줄을 잠가 읽은 뒤 일정 · 행사를 저장하고, 생긴 회차를 재고에 알리고({@link SessionAdded}, #54),
 * 조작 기록을 남긴다({@link EventScheduleEditing}).
 */
@Service
public class AddTimedSessionService implements AddTimedSessionUseCase {

    private final EventScheduleEditing editing;

    public AddTimedSessionService(EventScheduleEditing editing) {
        this.editing = editing;
    }

    /** 조작 기록의 요약 — "회차 추가 · 2026-03-14 19:00 (2단계)". */
    static String operationSummary(LocalDate date, TimedSessionTime time) {
        return "회차 추가 · " + ScheduleResults.sessionLabel(date, time) + " (2단계)";
    }

    @Override
    @Transactional
    public EventScheduleResult add(AddTimedSessionCommand command) {
        return editing.edit(command.actingStaff(), command.eventId(), (event, schedule, now) -> {
            event.addTimedSession(schedule, command.date(), command.time(), now);
            return Optional.of(operationSummary(command.date(), command.time()));
        });
    }
}
