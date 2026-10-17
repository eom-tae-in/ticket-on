package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.RemoveScheduleDateCommand;
import com.ticketon.application.event.port.in.RemoveScheduleDateUseCase;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.SessionRemoved;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 날짜 삭제(위저드 2단계) — 지우는 규칙(수정 범위, 그 날짜의 회차 함께 지우기, 진행 단계 · 마지막 수정 시각)은 {@link Event#removeScheduleDate}가
 * 갖고, 여기서는 줄을 잠가 읽은 뒤 일정 · 행사를 저장하고(지운 날짜 · 회차는 줄을 남기고 지운 시각을 적는다), 지운 회차마다 재고에 알리고
 * ({@link SessionRemoved}, #54), 조작 기록을 남긴다({@link EventScheduleEditing}).
 */
@Service
public class RemoveScheduleDateService implements RemoveScheduleDateUseCase {

    private final EventScheduleEditing editing;

    public RemoveScheduleDateService(EventScheduleEditing editing) {
        this.editing = editing;
    }

    /** 조작 기록의 요약 — "날짜 삭제 · 2026-05-03 (2단계)". */
    static String operationSummary(LocalDate date) {
        return "날짜 삭제 · " + date + " (2단계)";
    }

    @Override
    @Transactional
    public void remove(RemoveScheduleDateCommand command) {
        editing.edit(command.actingStaff(), command.eventId(), (event, schedule, now) -> {
            event.removeScheduleDate(schedule, command.date(), now);
            return Optional.of(operationSummary(command.date()));
        });
    }
}
