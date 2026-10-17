package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.RemoveSessionCommand;
import com.ticketon.application.event.port.in.RemoveSessionUseCase;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.SessionRemoved;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 삭제(위저드 2단계) — 지우는 규칙(수정 범위, 비게 된 날짜 빼기, 진행 단계 · 마지막 수정 시각)은 {@link Event#removeSession}이 갖고, 여기서는
 * 줄을 잠가 읽은 뒤 일정 · 행사를 저장하고(지운 회차는 줄을 남기고 지운 시각을 적는다), 재고에 알리고({@link SessionRemoved}, #54), 조작 기록을
 * 남긴다({@link EventScheduleEditing}).
 */
@Service
public class RemoveSessionService implements RemoveSessionUseCase {

    private final EventScheduleEditing editing;

    public RemoveSessionService(EventScheduleEditing editing) {
        this.editing = editing;
    }

    /** 조작 기록의 요약 — "회차 삭제 · 2026-03-14 19:00 (2단계)", 종일형은 "회차 삭제 · 2026-05-03 (2단계)". */
    static String operationSummary(String sessionLabel) {
        return "회차 삭제 · " + sessionLabel + " (2단계)";
    }

    @Override
    @Transactional
    public void remove(RemoveSessionCommand command) {
        editing.edit(command.actingStaff(), command.eventId(), (event, schedule, now) -> {
            String label = ScheduleResults.sessionLabel(schedule, command.sessionId());
            event.removeSession(schedule, command.sessionId(), now);
            return Optional.of(operationSummary(label));
        });
    }
}
