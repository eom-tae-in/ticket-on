package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.ChangeSessionPriceAdjustmentCommand;
import com.ticketon.application.event.port.in.ChangeSessionPriceAdjustmentUseCase;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.SessionPriceAdjustment;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회차 가격 조정 — 바꾸는 규칙(가격의 수정 범위, 같은 값이면 바꾸지 않음, 진행 단계 · 마지막 수정 시각)은 {@link Event#changeSessionPriceAdjustment}가
 * 갖고, 여기서는 줄을 잠가 읽은 뒤 바뀌었으면 일정 · 행사를 저장하고 조작 기록을 남긴다({@link EventScheduleEditing}). 조정한 가격의 계산은 #48이
 * 한다.
 */
@Service
public class ChangeSessionPriceAdjustmentService implements ChangeSessionPriceAdjustmentUseCase {

    private final EventScheduleEditing editing;

    public ChangeSessionPriceAdjustmentService(EventScheduleEditing editing) {
        this.editing = editing;
    }

    /** 조작 기록의 요약 — "가격 조정 변경 · 2026-03-15 19:00 · +10,000원 (2단계)". */
    static String operationSummary(String sessionLabel, SessionPriceAdjustment priceAdjustment) {
        return "가격 조정 변경 · " + sessionLabel + " · " + ScheduleResults.priceAdjustmentLabel(priceAdjustment) + " (2단계)";
    }

    @Override
    @Transactional
    public EventScheduleResult change(ChangeSessionPriceAdjustmentCommand command) {
        return editing.edit(command.actingStaff(), command.eventId(), (event, schedule, now) -> {
            if (!event.changeSessionPriceAdjustment(schedule, command.sessionId(), command.priceAdjustment(), now)) {
                return Optional.empty();
            }
            return Optional.of(operationSummary(ScheduleResults.sessionLabel(schedule, command.sessionId()), command.priceAdjustment()));
        });
    }
}
