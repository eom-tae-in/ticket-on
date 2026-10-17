package com.ticketon.application.event.service;

import com.ticketon.application.event.port.in.ChangeDateOperatingHoursCommand;
import com.ticketon.application.event.port.in.ChangeDateOperatingHoursUseCase;
import com.ticketon.application.event.port.in.EventScheduleResult;
import com.ticketon.domain.event.Event;
import com.ticketon.domain.event.OperatingHours;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 날짜별 운영 시간(종일형) — 덮어쓰거나 지우는 규칙(회차 방식, 그날 회차의 시간 수정 범위, 진행 단계 · 마지막 수정 시각)은
 * {@link Event#changeDateOperatingHours}가 갖고, 여기서는 줄을 잠가 읽은 뒤 바뀌었으면 일정 · 행사를 저장하고 조작 기록을 남긴다
 * ({@link EventScheduleEditing}). 같은 값이면 저장도 기록도 하지 않는다.
 */
@Service
public class ChangeDateOperatingHoursService implements ChangeDateOperatingHoursUseCase {

    private final EventScheduleEditing editing;

    public ChangeDateOperatingHoursService(EventScheduleEditing editing) {
        this.editing = editing;
    }

    /** 조작 기록의 요약 — "운영 시간 덮어쓰기 · 2026-05-04 10:00~20:00 (2단계)", 지우면 "운영 시간 기본값으로 · 2026-05-04 (2단계)". */
    static String operationSummary(LocalDate date, OperatingHours operatingHours) {
        if (operatingHours == null) {
            return "운영 시간 기본값으로 · " + date + " (2단계)";
        }
        return "운영 시간 덮어쓰기 · " + date + " " + ScheduleResults.hoursLabel(operatingHours) + " (2단계)";
    }

    @Override
    @Transactional
    public EventScheduleResult change(ChangeDateOperatingHoursCommand command) {
        return editing.edit(command.actingStaff(), command.eventId(), (event, schedule, now) -> {
            if (!event.changeDateOperatingHours(schedule, command.date(), command.operatingHours(), now)) {
                return Optional.empty();
            }
            return Optional.of(operationSummary(command.date(), command.operatingHours()));
        });
    }
}
