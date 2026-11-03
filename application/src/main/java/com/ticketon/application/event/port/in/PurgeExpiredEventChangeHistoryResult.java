package com.ticketon.application.event.port.in;

import java.util.List;

/**
 * 보관 기간이 지난 행사 변경 이력 지우기 결과.
 *
 * @param purgedEventIds 이번에 이력을 지운 행사 id — 행사 id 차례
 */
public record PurgeExpiredEventChangeHistoryResult(List<Long> purgedEventIds) {

    public PurgeExpiredEventChangeHistoryResult {
        purgedEventIds = List.copyOf(purgedEventIds);
    }
}
