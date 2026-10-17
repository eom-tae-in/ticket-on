package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.ChangeSessionDateTimeCommand;
import com.ticketon.application.event.port.in.ChangeSessionDateTimeUseCase;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.domain.event.Event;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 일시 변경 — 바꾸는 규칙(회차 방식에 맞는 칸, 수정 범위, 예매가 열린 뒤 시작 1주일 전 잠금, 비어 있던 종료 시각 채우기, 날짜 · 시각 겹침,
 * 진행 단계 · 마지막 수정 시각)은 {@link Event#changeSessionDateTime}이 갖고, 여기서는 줄을 잠가 읽은 뒤 바뀌었으면 일정 · 행사를 저장하고 조작
 * 기록을 남긴다({@link EventScheduleEditing}). 회차는 그대로라 재고에 알리지 않는다. 같은 값이면 저장도 기록도 하지 않는다.
 */
@Service
public class ChangeSessionDateTimeService implements ChangeSessionDateTimeUseCase {

    private final EventScheduleEditing editing;

    public ChangeSessionDateTimeService(EventScheduleEditing editing) {
        this.editing = editing;
    }

    /** 조작 기록의 요약 — "회차 일시 변경 · 2026-03-14 19:00 → 2026-03-16 20:00 (2단계)", 종일형은 "2026-05-04 → 2026-05-10". */
    static String operationSummary(String before, String after) {
        return "회차 일시 변경 · " + before + " → " + after + " (2단계)";
    }

    @Override
    @Transactional
    public EventScheduleResult change(ChangeSessionDateTimeCommand command) {
        return editing.edit(command.actingStaff(), command.eventId(), (event, schedule, now) -> {
            String before = ScheduleResults.sessionLabel(schedule, command.sessionId());
            if (!event.changeSessionDateTime(schedule, command.sessionId(), command.date(), command.startTime(), command.endTime(),
                    now)) {
                return Optional.empty();
            }
            return Optional.of(operationSummary(before, ScheduleResults.sessionLabel(schedule, command.sessionId())));
        });
    }
}
