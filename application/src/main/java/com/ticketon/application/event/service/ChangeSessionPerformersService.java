package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.ChangeSessionPerformersCommand;
import com.ticketon.application.event.port.in.ChangeSessionPerformersUseCase;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.domain.event.Event;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 출연 — 바꾸는 규칙(수정 범위, 같은 값이면 바꾸지 않음, 진행 단계 · 마지막 수정 시각)은 {@link Event#changeSessionPerformers}가 갖고,
 * 여기서는 줄을 잠가 읽은 뒤 바뀌었으면 일정 · 행사를 저장하고 조작 기록을 남긴다({@link EventScheduleEditing}). 변경 이력은 #46이 남긴다.
 */
@Service
public class ChangeSessionPerformersService implements ChangeSessionPerformersUseCase {

    private final EventScheduleEditing editing;

    public ChangeSessionPerformersService(EventScheduleEditing editing) {
        this.editing = editing;
    }

    /** 조작 기록의 요약 — "출연 변경 · 2026-03-14 19:00 (2단계)". 출연 칸의 내용은 일정에 있다. */
    static String operationSummary(String sessionLabel) {
        return "출연 변경 · " + sessionLabel + " (2단계)";
    }

    @Override
    @Transactional
    public EventScheduleResult change(ChangeSessionPerformersCommand command) {
        return editing.edit(command.actingStaff(), command.eventId(), (event, schedule, now) -> {
            if (!event.changeSessionPerformers(schedule, command.sessionId(), command.performers(), now)) {
                return Optional.empty();
            }
            return Optional.of(operationSummary(ScheduleResults.sessionLabel(schedule, command.sessionId())));
        });
    }
}
