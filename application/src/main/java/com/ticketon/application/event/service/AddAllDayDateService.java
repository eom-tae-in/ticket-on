package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.AddAllDayDateCommand;
import com.ticketon.application.event.port.in.AddAllDayDateUseCase;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.SessionAdded;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 종일형 날짜 추가(위저드 2단계) — 더하는 규칙(수정 범위, 회차 방식, 날짜 겹침, 진행 단계 · 마지막 수정 시각)은 {@link Event#addAllDayDate}가
 * 갖고, 여기서는 줄을 잠가 읽은 뒤 일정 · 행사를 저장하고, 그 날짜의 회차를 재고에 알리고({@link SessionAdded}, #54), 조작 기록을 남긴다
 * ({@link EventScheduleEditing}).
 */
@Service
public class AddAllDayDateService implements AddAllDayDateUseCase {

    private final EventScheduleEditing editing;

    public AddAllDayDateService(EventScheduleEditing editing) {
        this.editing = editing;
    }

    /** 조작 기록의 요약 — "날짜 추가 · 2026-05-03 (2단계)". */
    static String operationSummary(LocalDate date) {
        return "날짜 추가 · " + date + " (2단계)";
    }

    @Override
    @Transactional
    public EventScheduleResult add(AddAllDayDateCommand command) {
        return editing.edit(command.actingStaff(), command.eventId(), (event, schedule, now) -> {
            event.addAllDayDate(schedule, command.date(), now);
            return Optional.of(operationSummary(command.date()));
        });
    }
}
