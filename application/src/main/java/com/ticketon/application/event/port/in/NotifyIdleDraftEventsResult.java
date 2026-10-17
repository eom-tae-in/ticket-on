package com.ticketon.application.event.port.in;

import java.util.List;

/**
 * 작성 중 행사 30일 미수정 알림 결과.
 *
 * @param notifiedEventIds 이번에 알린 행사 id — 행사 id 차례
 */
public record NotifyIdleDraftEventsResult(List<Long> notifiedEventIds) {

    public NotifyIdleDraftEventsResult {
        notifiedEventIds = List.copyOf(notifiedEventIds);
    }
}
